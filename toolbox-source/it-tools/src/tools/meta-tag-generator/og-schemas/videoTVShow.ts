import type { OGSchemaType } from '../OGSchemaType.type';
import { videoMovie } from './videoMovie';

export const videoTVShow: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.videoTvShow.name',
  elements: [...videoMovie.elements],
};
