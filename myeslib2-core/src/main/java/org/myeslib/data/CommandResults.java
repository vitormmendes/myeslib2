package org.myeslib.data;

import org.myeslib.core.Command;

import java.util.Collections;
import java.util.List;

public class CommandResults {

    private final UnitOfWork unitOfWork;
    private final List<Command> externalCommands;

    public CommandResults(UnitOfWork unitOfWork, List<Command> externalCommands) {
        this.unitOfWork = unitOfWork;
        this.externalCommands = externalCommands;
    }

    public CommandResults(UnitOfWork unitOfWork) {
        this.unitOfWork = unitOfWork;
        this.externalCommands = Collections.emptyList();
    }

    public UnitOfWork getUnitOfWork() {
        return unitOfWork;
    }

    public List<Command> getExternalCommands() {
        return Collections.unmodifiableList(externalCommands);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommandResults that = (CommandResults) o;

        if (!externalCommands.equals(that.externalCommands)) return false;
        if (!unitOfWork.equals(that.unitOfWork)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = unitOfWork.hashCode();
        result = 31 * result + externalCommands.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CommandResults{" +
                "unitOfWork=" + unitOfWork +
                ", externalCommands=" + externalCommands +
                '}';
    }
}
