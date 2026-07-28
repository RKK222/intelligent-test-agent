import type { OGSchemaType } from '../OGSchemaType.type';

export const article: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.article.name',
  elements: [
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.article.publishingDate',
      key: 'article:published_time',
      placeholder: 'tools.og-meta-generator.ui.schema.article.publishingDatePlaceholder',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.article.modificationDate',
      key: 'article:modified_time',
      placeholder: 'tools.og-meta-generator.ui.schema.article.modificationDatePlaceholder',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.article.expirationDate',
      key: 'article:expiration_time',
      placeholder: 'tools.og-meta-generator.ui.schema.article.expirationDatePlaceholder',
    },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.author', key: 'article:author', placeholder: 'tools.og-meta-generator.ui.schema.article.authorPlaceholder' },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.article.section',
      key: 'article:section',
      placeholder: 'tools.og-meta-generator.ui.schema.article.sectionPlaceholder',
    },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.tag', key: 'article:tag', placeholder: 'tools.og-meta-generator.ui.schema.article.tagPlaceholder' },
  ],
};
