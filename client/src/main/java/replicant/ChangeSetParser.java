package replicant;

import elemental2.core.Global;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import jsinterop.base.Js;
import replicant.messages.ChangeSet;
import replicant.messages.ChannelChange;
import replicant.messages.EntityChange;
import replicant.messages.EntityChangeDataImpl;

/**
 * This is the class responsible for parsing change sets.
 * This class includes a "test" JVM implementation that will be ignored
 * when GWT compilation takes place. It is not yet handle all the varied
 * types in entities nor filters. Not suitable outside tests.
 */
final class ChangeSetParser
{
  /**
   * The code to parse changesets. Extracted into a separate class so it can be vary by environment.
   */
  private static final ChangeSetParserSupport c_support = new ChangeSetParserSupport();

  @Nonnull
  static ChangeSet parseChangeSet( @Nonnull final String rawJsonData )
  {
    return c_support.parseChangeSet( rawJsonData );
  }

  /**
   * This is the class responsible for parsing change sets.
   * This is split into two classes so that gwt implementation is in base class and the JVM
   * implementation is in subclass but marked as GwtIncompatible so they are elided during compile.
   */
  static abstract class AbstractChangeSetParserSupport
  {
    @SuppressWarnings( "unused" )
    @Nonnull
    ChangeSet parseChangeSet( @Nonnull final String rawJsonData )
    {
      return Js.cast( Global.JSON.parse( rawJsonData ) );
    }
  }

  static class ChangeSetParserSupport
    extends AbstractChangeSetParserSupport
  {
    @GwtIncompatible
    @Nonnull
    @Override
    ChangeSet parseChangeSet( @Nonnull final String rawJsonData )
    {
      try ( final JsonReader reader = Json.createReader( new StringReader( rawJsonData ) ) )
      {
        final JsonObject object = reader.readObject();
        final int sequence = object.getInt( "last_id" );
        final Integer requestId =
          object.containsKey( "requestId" ) ? object.getJsonNumber( "requestId" ).intValue() : null;
        final String etag =
          object.containsKey( "etag" ) ? object.getString( "etag" ) : null;

        final ChannelChange[] fchannels = parseChannelChanges( object );
        final EntityChange[] entityChanges = parseEntityChanges( object );

        return ChangeSet.create( sequence, requestId, etag, parseChannels( object ), fchannels, entityChanges );
      }
    }

    @GwtIncompatible
    @Nullable
    private String[] parseChannels( @Nonnull final JsonObject object )
    {
      return object.containsKey( "channels" ) ?
             object.getJsonArray( "channels" )
               .stream()
               .map( value -> ( (JsonString) value ).getString() )
               .toArray( String[]::new ) :
             null;
    }

    @GwtIncompatible
    @Nullable
    private ChannelChange[] parseChannelChanges( @Nonnull final JsonObject object )
    {
      // TODO: Filters not yet supported properly
      return object.containsKey( "fchannels" ) ?
             object
               .getJsonArray( "fchannels" )
               .stream()
               .map( value -> (JsonObject) value )
               .map( change -> ChannelChange.create( change.getString( "channel" ),
                                                     change.getOrDefault( "filter", null ) ) )
               .toArray( ChannelChange[]::new ) :
             null;
    }

    @GwtIncompatible
    @Nullable
    private EntityChange[] parseEntityChanges( @Nonnull final JsonObject object )
    {
      if ( object.containsKey( "changes" ) )
      {
        final ArrayList<EntityChange> changes = new ArrayList<>();
        for ( final JsonValue value : object.getJsonArray( "changes" ) )
        {
          final JsonObject change = (JsonObject) value;

          final int id = change.getInt( "id" );
          final int typeId = change.getInt( "type" );

          final EntityChangeDataImpl changeData;
          if ( change.containsKey( "data" ) )
          {
            changeData = new EntityChangeDataImpl();
            final JsonObject data = change.getJsonObject( "data" );
            for ( final Map.Entry<String, JsonValue> entry : data.entrySet() )
            {
              final String key = entry.getKey();
              final JsonValue v = entry.getValue();
              final JsonValue.ValueType valueType = v.getValueType();
              if ( JsonValue.ValueType.NULL == valueType )
              {
                changeData.getData().put( key, null );
              }
              else if ( JsonValue.ValueType.FALSE == valueType )
              {
                changeData.getData().put( key, false );
              }
              else if ( JsonValue.ValueType.TRUE == valueType )
              {
                changeData.getData().put( key, true );
              }
              else if ( JsonValue.ValueType.NUMBER == valueType )
              {
                //TODO: Handle real/float values
                changeData.getData().put( key, ( (JsonNumber) v ).intValue() );
              }
              else
              {
                //TODO: Handle all the other types valid here
                assert JsonValue.ValueType.STRING == valueType;
                changeData.getData().put( key, ( (JsonString) v ).getString() );
              }
            }
          }
          else
          {
            changeData = null;
          }

          final List<String> entityChannels = new ArrayList<>();
          for ( final JsonValue channelReference : change.getJsonArray( "channels" ) )
          {
            final JsonString channel = (JsonString) channelReference;
            entityChannels.add( channel.getString() );
          }

          final String[] channels = entityChannels.toArray( new String[ 0 ] );
          changes.add( null == changeData ?
                       EntityChange.create( typeId, id, channels ) :
                       EntityChange.create( typeId, id, channels, changeData ) );
        }
        return changes.toArray( new EntityChange[ 0 ] );
      }
      else
      {
        return null;
      }
    }
  }
}
