/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.dataflow.sdk.coders;

import java.io.IOException;

/**
 * A {@code CoderException} is thrown if there is a problem encoding or
 * decoding a value.
 */
public class CoderException extends IOException {
  private static final long serialVersionUID = 0;

  public CoderException(String message) {
    super(message);
  }

  public CoderException(String message, Throwable cause) {
    super(message, cause);
  }

  public CoderException(Throwable cause) {
    super(cause);
  }
}
