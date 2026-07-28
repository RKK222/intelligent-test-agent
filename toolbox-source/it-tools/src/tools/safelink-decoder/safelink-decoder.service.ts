export function decodeSafeLinksURL(safeLinksUrl: string) {
  if (!safeLinksUrl.match(/\.safelinks\.protection\.outlook\.com/)) {
    throw new Error('INVALID_SAFELINK_URL');
  }

  return new URL(safeLinksUrl).searchParams.get('url');
}
