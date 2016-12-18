package org.realityforge.replicant.server.transport;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonAutoDetect( fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE, creatorVisibility = JsonAutoDetect.Visibility.NONE )
@JsonTypeName( "testFilter" )
public class TestFilter
{
  private int myField;

  public TestFilter()
  {
  }

  public TestFilter( final int myField )
  {
    this.myField = myField;
  }

  public int getMyField()
  {
    return myField;
  }
}
