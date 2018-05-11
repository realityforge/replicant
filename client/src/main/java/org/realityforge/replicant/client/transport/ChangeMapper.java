package org.realityforge.replicant.client.transport;

import javax.annotation.Nonnull;
import replicant.EntityChange;

/**
 * The ChangeMapper is responsible for applying changes to the world.
 *
 * <p>The mapper will be invoked for each change message and is responsible for;</p>
 *
 * <ul>
 * <li>creating the entity if it does not already exist</li>
 * <li>registering or de-registering the entity in the EntityRepository</li>
 * <li>applying the state changes to the entity</li>
 * </ul>
 *
 * <p>As the ChangeMapper is very specific to the domain model that is being replicated, the ChangeMapper
 * is typically generated from a description via a tool such as Domgen.</p>
 */
public interface ChangeMapper
{
  /**
   * Apply a single entityChange to the world.
   *
   * @param entityChange the EntityChange.
   * @return the entity that was created, updated or removed.
   */
  @Nonnull
  Object applyChange( @Nonnull EntityChange entityChange );
}
