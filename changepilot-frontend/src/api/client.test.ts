import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiClient } from './client';

describe('apiClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('preserves structured API errors', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'VALIDATION_ERROR',
            message: 'Request validation failed',
            fieldErrors: [{ field: 'title', message: 'Title is required' }]
          }),
          {
            status: 400,
            headers: { 'Content-Type': 'application/json' }
          }
        )
      )
    );

    await expect(apiClient.createChange({ title: '', description: '', risk: 'LOW', affectedComponents: [], criteria: [] })).rejects.toEqual(
      expect.objectContaining({
        status: 400,
        code: 'VALIDATION_ERROR',
        message: 'Request validation failed',
        fieldErrors: [{ field: 'title', message: 'Title is required' }]
      })
    );
  });

  it('encodes status queries and path segments', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      const payload = url.includes('/criteria/') ? {} : [];
      return Promise.resolve(new Response(JSON.stringify(payload), { status: 200 }));
    });
    vi.stubGlobal('fetch', fetchMock);

    await apiClient.listChanges('IN_PROGRESS');
    await apiClient.updateCriterionCompletion('change/1', 'criterion 2', true);

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      'http://localhost:8080/api/engineering-changes?status=IN_PROGRESS',
      { headers: { Accept: 'application/json' }, method: 'GET' }
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      'http://localhost:8080/api/engineering-changes/change%2F1/criteria/criterion%202',
      expect.objectContaining({ method: 'PATCH' })
    );
  });
});
