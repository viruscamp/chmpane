  1 /***************************************************************************
  2  *                        lzx.h - LZX decompression routines               *
  3  *                           -------------------                           *
  4  *                                                                         *
  5  *  maintainer: Jed Wing <jedwin@ugcs.caltech.edu>                         *
  6  *  source:     modified lzx.c from cabextract v0.5                        *
  7  *  notes:      This file was taken from cabextract v0.5, which was,       *
  8  *              itself, a modified version of the lzx decompression code   *
  9  *              from unlzx.                                                *
 10  ***************************************************************************/
 11 
 12 /***************************************************************************
 13  *                                                                         *
 14  *   Copyright(C) Stuart Caie                                              *
 15  *                                                                         *
 16  *   This library is free software; you can redistribute it and/or modify  *
 17  *   it under the terms of the GNU Lesser General Public License as        *
 18  *   published by the Free Software Foundation; either version 2.1 of the  *
 19  *   License, or (at your option) any later version.                       *
 20  *                                                                         *
 21  ***************************************************************************/
 22 
 23 #ifndef INCLUDED_LZX_H
 24 #define INCLUDED_LZX_H
 25 
 26 /* return codes */
 27 #define DECR_OK           (0)
 28 #define DECR_DATAFORMAT   (1)
 29 #define DECR_ILLEGALDATA  (2)
 30 #define DECR_NOMEMORY     (3)
 31 
 32 /* opaque state structure */
 33 struct LZXstate;
 34 
 35 /* create an lzx state object */
 36 struct LZXstate *LZXinit(int window);
 37 
 38 /* destroy an lzx state object */
 39 void LZXteardown(struct LZXstate *pState);
 40 
 41 /* reset an lzx stream */
 42 int LZXreset(struct LZXstate *pState);
 43 
 44 /* decompress an LZX compressed block */
 45 int LZXdecompress(struct LZXstate *pState,
 46                   unsigned char *inpos,
 47                   unsigned char *outpos,
 48                   int inlen,
 49                   int outlen);
 50 
 51 #endif /* INCLUDED_LZX_H */
 52 