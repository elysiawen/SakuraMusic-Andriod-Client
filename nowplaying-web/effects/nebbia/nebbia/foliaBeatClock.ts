// folia beatFx/clock 的类型 shim：只有水族箱的跟拍游标（nebbiaBeatCursor）读这个类型。
export type BeatFxEventKind = 'beat' | 'downbeat';

export interface BeatFxEvent {
  t: number;
  kind: BeatFxEventKind;
  impact?: number;
  tone?: { low: number; mid: number; high: number };
  barPos?: number | null;
  rhythmic?: boolean;
}
