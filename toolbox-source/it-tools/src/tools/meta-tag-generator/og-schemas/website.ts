import type { OGSchemaType } from '../OGSchemaType.type';

const typeOptions = [
  { label: 'tools.og-meta-generator.ui.schema.website.website', value: 'website' },
  { label: 'tools.og-meta-generator.ui.schema.article.name', value: 'article' },
  { label: 'tools.og-meta-generator.ui.schema.book.name', value: 'book' },
  { label: 'tools.og-meta-generator.ui.schema.profile.name', value: 'profile' },
  {
    type: 'group',
    label: 'tools.og-meta-generator.ui.schema.website.music',
    key: 'Music',
    children: [
      { label: 'tools.og-meta-generator.ui.schema.common.song', value: 'music.song' },
      { label: 'tools.og-meta-generator.ui.schema.website.musicAlbum', value: 'music.album' },
      { label: 'tools.og-meta-generator.ui.schema.website.playlist', value: 'music.playlist' },
      { label: 'tools.og-meta-generator.ui.schema.website.radioStation', value: 'music.radio_station' },
    ],
  },
  {
    type: 'group',
    label: 'tools.og-meta-generator.ui.schema.website.video',
    key: 'Video',
    children: [
      { label: 'tools.og-meta-generator.ui.schema.website.movie', value: 'video.movie' },
      { label: 'tools.og-meta-generator.ui.schema.website.episode', value: 'video.episode' },
      { label: 'tools.og-meta-generator.ui.schema.website.tvShow', value: 'video.tv_show' },
      { label: 'tools.og-meta-generator.ui.schema.website.otherVideo', value: 'video.other' },
    ],
  },
];

export const website: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.website.generalInformation',
  elements: [
    {
      type: 'select',
      label: 'tools.og-meta-generator.ui.schema.website.pageType',
      placeholder: 'tools.og-meta-generator.ui.schema.website.pageTypePlaceholder',
      key: 'type',
      options: typeOptions,
    },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.website.title', placeholder: 'tools.og-meta-generator.ui.schema.website.titlePlaceholder', key: 'title' },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.website.description',
      placeholder: 'tools.og-meta-generator.ui.schema.website.descriptionPlaceholder',
      key: 'description',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.website.pageUrl',
      placeholder: 'tools.og-meta-generator.ui.schema.website.pageUrlPlaceholder',
      key: 'url',
    },
  ],
};
