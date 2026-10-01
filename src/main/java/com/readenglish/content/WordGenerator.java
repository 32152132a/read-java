package com.readenglish.content;

import java.util.List;
import tools.jackson.databind.node.ObjectNode;

public interface WordGenerator {
  record Result(String word, ObjectNode config, String error) {
    public static Result success(String word, ObjectNode config) {
      return new Result(word, config, null);
    }

    public static Result failure(String word, String error) {
      return new Result(word, null, error);
    }
  }

  List<Result> generate(List<String> words);

  boolean available();
}
