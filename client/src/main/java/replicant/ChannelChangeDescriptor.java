package replicant;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import replicant.messages.ChannelChange;

final class ChannelChangeDescriptor
{
  enum Type
  {
    ADD, REMOVE, UPDATE
  }

  @Nonnull
  private final Type _type;
  @Nonnull
  private final ChannelAddress _address;
  @Nullable
  private final Object _filter;

  @Nonnull
  static ChannelChangeDescriptor from( final int schema, @Nonnull final String channelAction )
  {
    final Type type = '+' == channelAction.charAt( 0 ) ? Type.ADD : Type.REMOVE;
    final ChannelAddress address = toAddress( schema, channelAction );
    return new ChannelChangeDescriptor( type, address, null );
  }

  @Nonnull
  static ChannelChangeDescriptor from( final int schema, @Nonnull final ChannelChange channelChange )
  {
    final String channelAction = channelChange.getChannel();
    final Type type =
      '+' == channelAction.charAt( 0 ) ? Type.ADD : '-' == channelAction.charAt( 0 ) ? Type.REMOVE : Type.UPDATE;
    final ChannelAddress address = toAddress( schema, channelAction );
    return new ChannelChangeDescriptor( type, address, channelChange.getFilter() );
  }

  @Nonnull
  private static ChannelAddress toAddress( final int schema, @Nonnull final String channelAction )
  {
    final int offset = channelAction.indexOf( ".", 1 );
    final int channelId =
      Integer.parseInt( -1 == offset ? channelAction.substring( 1 ) : channelAction.substring( 1, offset ) );
    final Integer subChannelId = -1 == offset ? null : Integer.parseInt( channelAction.substring( offset + 1 ) );
    return new ChannelAddress( schema, channelId, subChannelId );
  }

  private ChannelChangeDescriptor( @Nonnull final Type type,
                                   @Nonnull final ChannelAddress address,
                                   @Nullable final Object filter )
  {
    _type = Objects.requireNonNull( type );
    _address = Objects.requireNonNull( address );
    _filter = filter;
  }

  @Nonnull
  Type getType()
  {
    return _type;
  }

  @Nonnull
  ChannelAddress getAddress()
  {
    return _address;
  }

  @Nullable
  Object getFilter()
  {
    return _filter;
  }
}
