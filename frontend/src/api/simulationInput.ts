export function parseSimulationInput(inputText: string): string[] {
  return inputText
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}