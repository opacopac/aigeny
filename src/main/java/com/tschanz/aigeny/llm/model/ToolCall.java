package com.tschanz.aigeny.llm.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a tool call requested by the assistant.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {

    private String id;
    private String type = "function";
    private FunctionCall function;

    public String getId()         { return id; }
    public void   setId(String id){ this.id = id; }

    public String getType()       { return type; }
    public void   setType(String t){ this.type = t; }

    public FunctionCall getFunction()      { return function; }
    public void         setFunction(FunctionCall f){ this.function = f; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {
        private String name;
        private String arguments; // JSON string

        public String getName()      { return name; }
        public void   setName(String n){ this.name = n; }

        public String getArguments() { return arguments; }
        public void   setArguments(String a){ this.arguments = a; }
    }
}

