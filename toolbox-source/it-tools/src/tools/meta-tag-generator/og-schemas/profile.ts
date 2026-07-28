import type { OGSchemaType } from '../OGSchemaType.type';

export const profile: OGSchemaType = {
  name: 'tools.og-meta-generator.ui.schema.profile.name',
  elements: [
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.profile.firstName',
      placeholder: 'tools.og-meta-generator.ui.schema.profile.firstNamePlaceholder',
      key: 'profile:first_name',
    },
    {
      type: 'input',
      label: 'tools.og-meta-generator.ui.schema.profile.lastName',
      placeholder: 'tools.og-meta-generator.ui.schema.profile.lastNamePlaceholder',
      key: 'profile:last_name',
    },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.profile.username', placeholder: 'tools.og-meta-generator.ui.schema.profile.usernamePlaceholder', key: 'profile:username' },
    { type: 'input', label: 'tools.og-meta-generator.ui.schema.profile.gender', placeholder: 'tools.og-meta-generator.ui.schema.profile.genderPlaceholder', key: 'profile:gender' },
  ],
};
