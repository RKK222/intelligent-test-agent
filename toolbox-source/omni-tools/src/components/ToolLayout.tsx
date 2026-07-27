import { Box } from '@mui/material';
import React, { ReactNode } from 'react';
import { Helmet } from 'react-helmet';
import ToolHeader from './ToolHeader';
import { useTranslation } from 'react-i18next';
import { ToolCategory } from '@tools/defineTool';
import { FullI18nKey } from '../i18n';
import { getI18nNamespaceFromToolCategory } from '../utils/string';
import type { IconifyIcon } from '@iconify/react';

export default function ToolLayout({
  children,
  i18n,
  type
}: {
  icon?: IconifyIcon | string;
  type: ToolCategory;
  fullPath: string;
  children: ReactNode;
  i18n: {
    name: FullI18nKey;
    description: FullI18nKey;
    shortDescription: FullI18nKey;
  };
}) {
  const { t } = useTranslation([
    'translation',
    getI18nNamespaceFromToolCategory(type)
  ]);
  const toolTitle = t(i18n.name);
  const toolDescription = t(i18n.description);

  return (
    <Box minHeight="100vh" sx={{ backgroundColor: 'background.default' }}>
      <Helmet>
        <title>{`${toolTitle} - 工具盒子`}</title>
      </Helmet>
      <Box
        component="header"
        position="sticky"
        top={0}
        zIndex={20}
        height="40px"
        display="flex"
        alignItems="center"
        gap={1}
        px={2}
        sx={{
          borderBottom: '1px solid',
          borderColor: 'divider',
          backgroundColor: 'rgba(255,255,255,.96)',
          backdropFilter: 'blur(8px)',
          fontSize: 12
        }}
      >
        <a href="/toolbox" style={{ color: '#315f9b', fontWeight: 700, textDecoration: 'none' }}>
          工具盒子
        </a>
        <span aria-hidden="true" style={{ color: '#8a94a4' }}>/</span>
        <strong style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {toolTitle}
        </strong>
      </Box>
      <Box width={{ xs: 'calc(100% - 28px)', md: '85%' }} maxWidth="1280px" mx="auto" pb={7}>
        <ToolHeader title={toolTitle} description={toolDescription} />
        {children}
      </Box>
    </Box>
  );
}
