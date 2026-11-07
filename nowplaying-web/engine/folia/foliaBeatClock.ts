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

/** A weak beat still peaks this high, so it is seen, just less. */
export const BEAT_FX_WEAK_BEAT_FLOOR = 0.35;

/** How high a beat's envelopes peak: 1 without accents, else its impact over the floor. */
export const resolveBeatFxAccentGain = (
  event: Pick<BeatFxEvent, 'impact' | 'rhythmic'> | null | undefined,
  trackScale: number,
): number => {
  if (event?.rhythmic === false) return 0;
  if (!event || event.impact === undefined) return trackScale;
  return (BEAT_FX_WEAK_BEAT_FLOOR + (1 - BEAT_FX_WEAK_BEAT_FLOOR) * event.impact) * trackScale;
};
