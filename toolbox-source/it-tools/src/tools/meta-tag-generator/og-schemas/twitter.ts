import type { OGSchemaType } from '../OGSchemaType.type';

export const twitter: OGSchemaType = {
  name: 'Twitter',
  elements: [
    {
      type: 'select',
      options: [
        { label: 'tools.og-meta-generator.ui.schema.twitter.summary', value: 'summary' },
        { label: 'tools.og-meta-generator.ui.schema.twitter.largeImage', value: 'summary_large_image' },
        { label: 'tools.og-meta-generator.ui.schema.twitter.application', value: 'app' },
        { label: 'tools.og-meta-generator.ui.schema.twitter.player', value: 'player' },
      ],
      label: 'tools.og-meta-generator.ui.schema.twitter.cardType',
      placeholder: 'tools.og-meta-generator.ui.schema.twitter.cardTypePlaceholder',
      key: 'twitter:card',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.twitter.siteAccount',
      placeholder: 'tools.og-meta-generator.ui.schema.twitter.siteAccountPlaceholder',
      key: 'twitter:site',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.twitter.creatorAccount',
      placeholder: 'tools.og-meta-generator.ui.schema.twitter.creatorAccountPlaceholder',
      key: 'twitter:creator',
    },
  ],
};
