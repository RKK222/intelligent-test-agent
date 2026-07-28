import type { OGSchemaType } from '../OGSchemaType.type';
import { videoMovie } from './videoMovie';

export const videoOther: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.videoOther.name',
  elements: [...videoMovie.elements],
};
