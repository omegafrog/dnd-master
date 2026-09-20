package com.dndmaster.relay.application;

import java.util.UUID;

public interface IdentityServicePort {

  /**
   * introspect user by token. return user's UUID
   *
   * @param token
   * @return
   */
  UUID introspectUser(String token);

}
