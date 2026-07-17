export function areDetailActionsDisabled(editMode: boolean, mutationBusy: boolean): boolean {
  return editMode || mutationBusy;
}
