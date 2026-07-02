export type SearchResultType = 'ACTION' | 'VEHICLE' | 'CLIENT' | 'RESERVATION' | 'CONTRACT' | 'EMPLOYEE' | 'PAYMENT';

export interface GlobalSearchResult {
  id: string;
  type: SearchResultType;
  entityId?: number;
  title: string;
  subtitle?: string;
  status?: string;
  route: string;
  icon?: string;
  score?: number;
}

export interface GlobalSearchResponse {
  query: string;
  results: GlobalSearchResult[];
}
