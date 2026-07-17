import { describe, expect, it } from 'vitest';
import { areDetailActionsDisabled } from './changePageState';

describe('areDetailActionsDisabled', () => {
  it('protects unsaved edits from detail mutations', () => {
    expect(areDetailActionsDisabled(true, false)).toBe(true);
  });

  it('serializes detail mutations while allowing idle detail actions', () => {
    expect(areDetailActionsDisabled(false, true)).toBe(true);
    expect(areDetailActionsDisabled(false, false)).toBe(false);
  });
});
