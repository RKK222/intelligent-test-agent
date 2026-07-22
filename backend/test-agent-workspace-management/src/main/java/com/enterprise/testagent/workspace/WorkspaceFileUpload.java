package com.enterprise.testagent.workspace;

/**
 * 单个工作区文件的流式上传会话。调用方必须按序追加分片，并在连接结束时完成或中止会话。
 */
public interface WorkspaceFileUpload {

    /** 服务端允许的原始二进制分片大小。 */
    int chunkBytes();

    /** 客户端在 begin 阶段声明的文件总字节数，仅用于完整性校验，不是上传上限。 */
    long expectedBytes();

    /** 已安全写入临时文件的原始字节数。 */
    long uploadedBytes();

    /** 按从 0 开始的连续序号追加一个 Base64 分片。 */
    void append(long index, String contentBase64);

    /** 校验总字节数并把临时文件发布到目标路径，返回最终文件大小。 */
    long complete();

    /** 关闭流并尽最大努力删除尚未发布的临时文件；允许重复调用。 */
    void abort();
}
