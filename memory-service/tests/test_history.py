from pathlib import Path

from testagent_memory_service.history import NoRawMessageHistoryManager


def test_raw_messages_are_never_persisted_but_derived_history_survives_restart(
    tmp_path: Path,
) -> None:
    database = tmp_path / "history.db"
    manager = NoRawMessageHistoryManager(str(database))
    manager.save_messages(
        [
            {"role": "user", "content": "几天前的完整聊天正文"},
            {"role": "assistant", "content": "完整回答"},
        ],
        "user_id=u1",
    )
    manager.add_history(
        "memory-1",
        None,
        "测试案例必须覆盖边界条件",
        "ADD",
        created_at="2026-08-09T00:00:00Z",
    )
    assert manager.raw_message_count() == 0
    assert manager.get_last_messages("user_id=u1") == []
    manager.close()

    reopened = NoRawMessageHistoryManager(str(database))
    assert reopened.raw_message_count() == 0
    history = reopened.get_history("memory-1")
    assert len(history) == 1
    assert history[0]["new_memory"] == "测试案例必须覆盖边界条件"
    reopened.close()
