package com.mrabdul.tools;

public interface CliCommand {
    String id();
    String description();
    int run(String[] args) throws Exception; // return process exit code
}
