import type { OGSchemaType } from '../OGSchemaType.type';

export const musicSong: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.musicSong.name',
  elements: [
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.duration', placeholder: 'tools.og-meta-generator.ui.schema.musicSong.durationPlaceholder', key: 'music:duration' },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.musicSong.album', placeholder: 'tools.og-meta-generator.ui.schema.musicSong.albumPlaceholder', key: 'music:album' },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.common.disc',
      placeholder: 'tools.og-meta-generator.ui.schema.musicSong.discPlaceholder',
      key: 'music:album:disk',
    },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.track', placeholder: 'tools.og-meta-generator.ui.schema.musicSong.trackPlaceholder', key: 'music:album:track' },
    {
      type: 'input-multiple',
      label: 'tools.og-meta-generator.ui.schema.common.musician',
      placeholder: 'tools.og-meta-generator.ui.schema.common.musicianPlaceholder',
      key: 'music:musician',
    },
  ],
};
