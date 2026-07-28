import type { OGSchemaType } from '../OGSchemaType.type';

export const musicPlaylist: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.musicPlaylist.name',
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
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.creator', key: 'music:creator', placeholder: 'tools.og-meta-generator.ui.schema.musicPlaylist.creatorPlaceholder' },
  ],
};
