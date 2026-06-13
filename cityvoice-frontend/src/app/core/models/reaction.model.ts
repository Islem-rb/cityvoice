export enum TypeReaction {
  JAIME   = 'JAIME',
  UTILE   = 'UTILE',
  BRAVO   = 'BRAVO',
  SOUTIEN = 'SOUTIEN'
}

export interface ReactionCountDto {
  type:  TypeReaction;
  count: number;
}

export interface ReactorDto {
  userId:   string;
  userName: string;
  type:     TypeReaction;
}

export interface ReactionSummaryDto {
  counts:       ReactionCountDto[];
  userReaction: TypeReaction | null;
  total:        number;
  reactors:     ReactorDto[];
}

export interface CreateReactionRequest {
  userId:   string;
  userName: string;
  type:     TypeReaction;
}
