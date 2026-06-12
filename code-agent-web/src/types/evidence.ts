export interface EvidenceRecord {
  id: number
  evidenceNo: string
  taskNo: string
  sourceType?: string
  sourceUri?: string
  rawRef?: string
  title?: string
  summary?: string
  score?: number
  metadata?: string
  createdAt?: string
}
