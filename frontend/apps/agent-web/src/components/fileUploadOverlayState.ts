export type FileUploadOverlayState = {
  fileName: string;
  fileIndex: number;
  fileCount: number;
  fileUploadedBytes: number;
  fileBytes: number;
  completedBytes: number;
  totalBytes: number;
};

export function initialFileUploadOverlayState(files: File[]): FileUploadOverlayState {
  const first = files[0];
  return {
    fileName: first?.name ?? "",
    fileIndex: files.length > 0 ? 1 : 0,
    fileCount: files.length,
    fileUploadedBytes: 0,
    fileBytes: first?.size ?? 0,
    completedBytes: 0,
    totalBytes: files.reduce((total, file) => total + file.size, 0)
  };
}
