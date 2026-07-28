import { cp, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const projectRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const runtime = resolve(projectRoot, 'dist/runtime');

await copyDirectory('node_modules/tesseract.js-core', 'tesseract/core');
await copyFile('node_modules/tesseract.js/dist/worker.min.js', 'tesseract/worker.min.js');
await copyDirectory('runtime-assets/tesseract/lang', 'tesseract/lang');
await copyDirectory('runtime-assets/imgly', 'imgly');
await copyFile('node_modules/@ffmpeg/core/dist/esm/ffmpeg-core.js', 'ffmpeg/ffmpeg-core.js');
await copyFile('node_modules/@ffmpeg/core/dist/esm/ffmpeg-core.wasm', 'ffmpeg/ffmpeg-core.wasm');
await copyFile('runtime-assets/ghostscript/gs-worker.wasm', 'ghostscript/gs-worker.wasm');

async function copyDirectory(source, target) {
  const destination = resolve(runtime, target);
  await mkdir(destination, { recursive: true });
  await cp(resolve(projectRoot, source), destination, { recursive: true });
}

async function copyFile(source, target) {
  const destination = resolve(runtime, target);
  await mkdir(dirname(destination), { recursive: true });
  await cp(resolve(projectRoot, source), destination);
}
