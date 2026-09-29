package com.readenglish.content;

import tools.jackson.databind.node.ObjectNode;

public interface WordGenerator {
  ObjectNode generate(String word);

  boolean available();
}
