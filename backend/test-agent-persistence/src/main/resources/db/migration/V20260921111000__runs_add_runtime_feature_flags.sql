-- 固化每次 Run 创建时的 RTK/Caveman 能力快照；历史 Run 保持 NULL，避免把未知误标为关闭。
alter table runs add column rtk_enabled boolean;
alter table runs add column concise_output_selected boolean;

comment on column runs.rtk_enabled is 'Run 创建时受管 OpenCode 的 RTK 命令改写开关快照；历史或未知时为空';
comment on column runs.concise_output_selected is '本 Run 是否选择或调用 concise-output Skill；历史或未知时为空';
