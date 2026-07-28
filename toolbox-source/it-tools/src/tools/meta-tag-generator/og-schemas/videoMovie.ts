import type { OGSchemaType } from '../OGSchemaType.type';

export const videoMovie: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.videoMovie.name',
  elements: [
    {
      type: 'input-multiple',
      label: 'tools.og-meta-generator.ui.schema.videoMovie.actor',
      key: 'video:actor',
      placeholder: 'tools.og-meta-generator.ui.schema.videoMovie.actorPlaceholder',
    },
    // { type: 'input', label: 'Actor role', key: 'video:actor:role', placeholder: 'The role they played...' },
    {
      type: 'input-multiple',
      label: 'tools.og-meta-generator.ui.schema.videoMovie.director',
      key: 'video:director',
      placeholder: 'tools.og-meta-generator.ui.schema.videoMovie.directorPlaceholder',
    },
    { type: 'input-multiple', label: 'tools.og-meta-generator.ui.schema.videoMovie.writer', key: 'video:writer', placeholder: 'tools.og-meta-generator.ui.schema.videoMovie.writerPlaceholder' },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.duration', key: 'video:duration', placeholder: 'tools.og-meta-generator.ui.schema.videoMovie.durationPlaceholder' },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.common.releaseDate',
      key: 'video:release_date',
      placeholder: 'tools.og-meta-generator.ui.schema.videoMovie.releaseDatePlaceholder',
    },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.tag', key: 'video:tag', placeholder: 'tools.og-meta-generator.ui.schema.videoMovie.tagPlaceholder' },
  ],
};
