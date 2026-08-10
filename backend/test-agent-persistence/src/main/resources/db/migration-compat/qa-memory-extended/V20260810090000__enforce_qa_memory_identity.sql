-- Mem0 逻辑记忆在平台治理面只能对应一条记录；NULL 仍允许用于尚未同步的兼容记录。
alter table qa_memories
    add constraint uk_qa_memories_mem0_memory_id unique (mem0_memory_id);
