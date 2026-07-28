import type { OGSchemaType } from '../OGSchemaType.type';

export const book: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.book.name',
  elements: [
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.author', key: 'book:author', placeholder: 'tools.og-meta-generator.ui.schema.book.authorPlaceholder' },
    { type: 'input', label: 'ISBN', key: 'book:isbn', placeholder: 'tools.og-meta-generator.ui.schema.book.isbnPlaceholder' },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.common.releaseDate',
      key: 'book:release_date',
      placeholder: 'tools.og-meta-generator.ui.schema.book.releaseDatePlaceholder',
    },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.tag', key: 'book:tag', placeholder: 'tools.og-meta-generator.ui.schema.book.tagPlaceholder' },
  ],
};
