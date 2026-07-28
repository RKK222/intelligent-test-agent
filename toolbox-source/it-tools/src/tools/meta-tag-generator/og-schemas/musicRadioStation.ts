import type { OGSchemaType } from '../OGSchemaType.type';

export const musicRadioStation: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.musicRadio.name',
  elements: [
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.common.creator', key: 'music:creator', placeholder: 'tools.og-meta-generator.ui.schema.musicRadio.creatorPlaceholder' },
  ],
};
