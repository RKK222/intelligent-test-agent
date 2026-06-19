import { ReadonlyTranscript } from "../../../components/ReadonlyTranscript";

export default async function SharePage({ params }: { params: Promise<{ sessionId: string }> }) {
  const { sessionId } = await params;
  return <ReadonlyTranscript sessionId={sessionId} />;
}
