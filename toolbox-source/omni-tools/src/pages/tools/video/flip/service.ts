import { FFmpeg } from '@ffmpeg/ffmpeg';
import { ffmpegLoadOptions } from '@/lib/runtime-assets';
import { fetchFile } from '@ffmpeg/util';
import { FlipOrientation } from './types';

const ffmpeg = new FFmpeg();

export async function flipVideo(
  input: File,
  orientation: FlipOrientation
): Promise<File> {
  if (!ffmpeg.loaded) {
    await ffmpeg.load(ffmpegLoadOptions());
  }

  const inputName = 'input.mp4';
  const outputName = 'output.mp4';
  await ffmpeg.writeFile(inputName, await fetchFile(input));

  const flipMap: Record<FlipOrientation, string> = {
    horizontal: 'hflip',
    vertical: 'vflip'
  };
  const flipFilter = flipMap[orientation];

  const args = ['-i', inputName];
  if (flipFilter) {
    args.push('-vf', flipFilter);
  }

  args.push('-c:v', 'libx264', '-preset', 'ultrafast', outputName);

  await ffmpeg.exec(args);

  const flippedData = await ffmpeg.readFile(outputName);
  return new File(
    [new Blob([flippedData as any], { type: 'video/mp4' })],
    `${input.name.replace(/\.[^/.]+$/, '')}_flipped.mp4`,
    { type: 'video/mp4' }
  );
}
