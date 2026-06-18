package com.example.GitTree.model;

public class GithubTreeResponse {
    
    private String name;
    private String type;
    private Integer byteSize;
    
    public GithubTreeResponse() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Integer getByteSize() { return byteSize; }
    public void setByteSize(Integer byteSize) { this.byteSize = byteSize; }
}
