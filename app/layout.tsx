import type { Metadata } from 'next';
export const metadata: Metadata = { title:'KosherHub — Less noise. More life.', description:'An open-source home for kosher phones, ROMs, shops, and community knowledge.' };
export default function Layout({children}:{children:React.ReactNode}){return <html lang="en"><body style={{margin:0}}>{children}</body></html>}
