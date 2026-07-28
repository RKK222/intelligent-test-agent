import { Box, Typography } from '@mui/material';

/** 派生应用只保留当前工具说明，移除面包屑、收藏、分类和上游图标。 */
export default function ToolHeader({
  title,
  description
}: {
  title: string;
  description: string;
}) {
  return (
    <Box py={{ xs: 3, md: 4 }} maxWidth="760px">
      <Typography component="h1" fontSize={{ xs: 26, md: 30 }} fontWeight={650} letterSpacing="-.025em">
        {title}
      </Typography>
      <Typography mt={1} color="text.secondary" fontSize={14} lineHeight={1.7}>
        {description}
      </Typography>
    </Box>
  );
}
