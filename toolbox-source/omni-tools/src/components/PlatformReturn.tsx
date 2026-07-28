import { useEffect } from 'react';

/** 套件首页和未知路由只负责返回平台，不暴露上游门户。 */
export default function PlatformReturn() {
  useEffect(() => window.location.replace('/toolbox'), []);
  return (
    <main style={{
      minHeight: '100vh',
      display: 'grid',
      placeContent: 'center',
      gap: 10,
      padding: 24,
      textAlign: 'center',
      background: '#f3f5f7',
      color: '#172033'
    }}>
      <h1 style={{ margin: 0 }}>工具不可用</h1>
      <p style={{ margin: 0 }}>该地址不是当前离线目录中的工具，正在返回平台工具盒子。</p>
      <a href="/toolbox" style={{ color: '#315f9b' }}>返回工具盒子</a>
    </main>
  );
}
