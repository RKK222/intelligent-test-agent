import { Box, useTheme } from '@mui/material';
import React, { useContext, useRef } from 'react';
import { CustomSnackBarContext } from '../../contexts/CustomSnackBarContext';
import InputHeader from '../InputHeader';
import InputFooter from './InputFooter';
import { useTranslation } from 'react-i18next';
import Editor, { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker';
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker';
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker';
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker';
import {
  globalInputHeight,
  codeInputHeightOffset
} from '../../config/uiConfig';
import { copyTextWithHttpFallback } from '@utils/clipboard';

// 上游 loader 默认访问 jsDelivr；显式注入 npm 包和本地 Worker，确保代码编辑工具可完全离线运行。
(self as unknown as {
  MonacoEnvironment: { getWorker(moduleId: string, label: string): Worker };
}).MonacoEnvironment = {
  getWorker(_moduleId: string, label: string) {
    if (label === 'json') return new jsonWorker();
    if (['css', 'scss', 'less'].includes(label)) return new cssWorker();
    if (['html', 'handlebars', 'razor'].includes(label)) return new htmlWorker();
    if (['typescript', 'javascript'].includes(label)) return new tsWorker();
    return new editorWorker();
  }
};
loader.config({ monaco });

export default function ToolCodeInput({
  value,
  onChange,
  title = 'Input text',
  language
}: {
  title?: string;
  value: string;
  language: string;
  onChange: (value: string) => void;
}) {
  const { t } = useTranslation();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const theme = useTheme();

  const handleCopy = () => {
    copyTextWithHttpFallback(value)
      .then(() => showSnackBar(t('toolTextInput.copied'), 'success'))
      .catch((err) => {
        showSnackBar(t('toolTextInput.copyFailed', { error: err }), 'error');
      });
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (e) => {
        const text = e.target?.result;
        if (typeof text === 'string') {
          onChange(text);
        }
      };
      reader.readAsText(file);
    }
  };

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  return (
    <Box>
      <InputHeader title={title || t('toolTextInput.input')} />
      <Box
        height={`${globalInputHeight + codeInputHeightOffset}px`} // The +codeInputHeightOffset compensates for internal padding/border differences between Monaco Editor and MUI TextField
        sx={{
          display: 'flex',
          flexDirection: 'column'
        }}
      >
        <Box
          sx={(theme) => ({
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            backgroundColor: 'background.paper',
            '.monaco-editor': {
              height: '100% !important',
              outline: 'none !important',
              '.overflow-guard': {
                height: '100% !important',
                border:
                  theme.palette.mode === 'light'
                    ? '1px solid rgba(0, 0, 0, 0.23)'
                    : '1px solid rgba(255, 255, 255, 0.23)',
                borderRadius: 1,
                transition: theme.transitions.create(
                  ['border-color', 'background-color'],
                  {
                    duration: theme.transitions.duration.shorter
                  }
                )
              },
              '&:hover .overflow-guard': {
                borderColor: theme.palette.text.primary
              }
            },
            '.decorationsOverviewRuler': {
              display: 'none !important'
            }
          })}
        >
          <Editor
            height="100%"
            language={language}
            theme={theme.palette.mode === 'dark' ? 'vs-dark' : 'light'}
            value={value}
            onChange={(value) => onChange(value ?? '')}
            options={{
              scrollbar: {
                vertical: 'visible',
                horizontal: 'visible',
                verticalScrollbarSize: 10,
                horizontalScrollbarSize: 10,
                alwaysConsumeMouseWheel: false
              }
            }}
          />
        </Box>
        <InputFooter handleCopy={handleCopy} handleImport={handleImportClick} />
        <input
          type="file"
          accept="*"
          ref={fileInputRef}
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
      </Box>
    </Box>
  );
}
