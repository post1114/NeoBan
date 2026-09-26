package com.neoban.common.storage;

import java.sql.SQLException;

public class StorageException extends RuntimeException {

    public StorageException(String operation, SQLException cause) {
        super("Storage error in " + operation + ": " + cause.getMessage(), cause);
    }
}
