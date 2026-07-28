import type { OGSchemaType } from '../OGSchemaType.type';

export const image: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.image.name',
  elements: [
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.image.url',
      placeholder: 'tools.og-meta-generator.ui.schema.image.urlPlaceholder',
      key: 'image',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.image.alt',
      placeholder: 'tools.og-meta-generator.ui.schema.image.altPlaceholder',
      key: 'image:alt',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.image.width',
      placeholder: 'tools.og-meta-generator.ui.schema.image.widthPlaceholder',
      key: 'image:width',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.image.height',
      placeholder: 'tools.og-meta-generator.ui.schema.image.heightPlaceholder',
      key: 'image:height',
    },
  ],
};
