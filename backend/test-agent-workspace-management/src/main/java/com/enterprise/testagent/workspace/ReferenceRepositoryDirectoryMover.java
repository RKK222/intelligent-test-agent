package com.enterprise.testagent.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 引用资产新副本目录移动边界；生产只允许同文件系统原子 rename，测试可模拟文件系统能力。 */
@FunctionalInterface
interface ReferenceRepositoryDirectoryMover {

    void moveAtomically(Path source, Path target) throws IOException;

    static ReferenceRepositoryDirectoryMover filesystem() {
        return (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
    }
}
