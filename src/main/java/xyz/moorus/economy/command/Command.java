package xyz.moorus.economy.command;

public interface Command {
    String getName();
    void execute(String sender, String[] args);
}
