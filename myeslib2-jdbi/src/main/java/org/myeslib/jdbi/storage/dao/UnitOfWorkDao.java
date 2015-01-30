package org.myeslib.jdbi.storage.dao;

import org.myeslib.core.Command;
import org.myeslib.data.UnitOfWork;

import java.util.List;

public interface UnitOfWorkDao<K> {

    List<UnitOfWork> getFull(K id);

    List<UnitOfWork> getPartial(K id, Long biggerThanThisVersion);

    void append(Command<K> command, UnitOfWork unitOfWork);

    Command<K> getCommand(K commandId);
}
