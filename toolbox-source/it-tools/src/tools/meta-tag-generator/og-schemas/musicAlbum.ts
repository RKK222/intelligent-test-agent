import type { OGSchemaType } from '../OGSchemaType.type';

export const musicAlbum: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.musicAlbum.name',
  elements: [
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.song', key: 'music:song', placeholder: 'tools.og-meta-generator.ui.schema.musicAlbum.songPlaceholder' },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.common.disc',
      key: 'music:song:disc',
      placeholder: 'tools.og-meta-generator.ui.schema.common.discReversePlaceholder',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.common.track',
      key: 'music:song:track',
      placeholder: 'tools.og-meta-generator.ui.schema.common.trackReversePlaceholder',
    },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.musician', key: 'music:musician', placeholder: 'tools.og-meta-generator.ui.schema.common.musicianPlaceholder' },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.common.releaseDate',
      key: 'music:release_date',
      placeholder: 'tools.og-meta-generator.ui.schema.musicAlbum.releaseDatePlaceholder',
    },
  ],
};
