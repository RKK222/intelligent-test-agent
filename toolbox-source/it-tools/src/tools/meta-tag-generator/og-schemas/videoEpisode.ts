import type { OGSchemaType } from '../OGSchemaType.type';
import { videoMovie } from './videoMovie';

export const videoEpisode: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.videoEpisode.name',
  elements: [
    ...videoMovie.elements,
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.videoEpisode.series', key: 'video:series', placeholder: 'tools.og-meta-generator.ui.schema.videoEpisode.seriesPlaceholder' },
  ],
};
