  1 /***************************************************************************
  2  *                        lzx.c - LZX decompression routines               *
  3  *                           -------------------                           *
  4  *                                                                         *
  5  *  maintainer: Jed Wing <jedwin@ugcs.caltech.edu>                         *
  6  *  source:     modified lzx.c from cabextract v0.5                        *
  7  *  notes:      This file was taken from cabextract v0.5, which was,       *
  8  *              itself, a modified version of the lzx decompression code   *
  9  *              from unlzx.                                                *
 10  *                                                                         *
 11  *  platforms:  In its current incarnation, this file has been tested on   *
 12  *              two different Linux platforms (one, redhat-based, with a   *
 13  *              2.1.2 glibc and gcc 2.95.x, and the other, Debian, with    *
 14  *              2.2.4 glibc and both gcc 2.95.4 and gcc 3.0.2).  Both were *
 15  *              Intel x86 compatible machines.                             *
 16  ***************************************************************************/
 17 
 18 /***************************************************************************
 19  *                                                                         *
 20  *   Copyright(C) Stuart Caie                                              *
 21  *                                                                         *
 22  *   This library is free software; you can redistribute it and/or modify  *
 23  *   it under the terms of the GNU Lesser General Public License as        *
 24  *   published by the Free Software Foundation; either version 2.1 of the  *
 25  *   License, or (at your option) any later version.                       *
 26  *                                                                         *
 27  ***************************************************************************/
 28 
 29 #include "lzx.h"
 30 #include <stdio.h>
 31 #include <stdlib.h>
 32 #include <string.h>
 33 
 34 /* sized types */
 35 typedef unsigned char  UBYTE; /* 8 bits exactly    */
 36 typedef unsigned short UWORD; /* 16 bits (or more) */
 37 typedef unsigned int   ULONG; /* 32 bits (or more) */
 38 typedef   signed int    LONG; /* 32 bits (or more) */
 39 
 40 /* some constants defined by the LZX specification */
 41 #define LZX_MIN_MATCH                (2)
 42 #define LZX_MAX_MATCH                (257)
 43 #define LZX_NUM_CHARS                (256)
 44 #define LZX_BLOCKTYPE_INVALID        (0)   /* also blocktypes 4-7 invalid */
 45 #define LZX_BLOCKTYPE_VERBATIM       (1)
 46 #define LZX_BLOCKTYPE_ALIGNED        (2)
 47 #define LZX_BLOCKTYPE_UNCOMPRESSED   (3)
 48 #define LZX_PRETREE_NUM_ELEMENTS     (20)
 49 #define LZX_ALIGNED_NUM_ELEMENTS     (8)   /* aligned offset tree #elements */
 50 #define LZX_NUM_PRIMARY_LENGTHS      (7)   /* this one missing from spec! */
 51 #define LZX_NUM_SECONDARY_LENGTHS    (249) /* length tree #elements */
 52 
 53 /* LZX huffman defines: tweak tablebits as desired */
 54 #define LZX_PRETREE_MAXSYMBOLS  (LZX_PRETREE_NUM_ELEMENTS)
 55 #define LZX_PRETREE_TABLEBITS   (6)
 56 #define LZX_MAINTREE_MAXSYMBOLS (LZX_NUM_CHARS + 50*8)
 57 #define LZX_MAINTREE_TABLEBITS  (12)
 58 #define LZX_LENGTH_MAXSYMBOLS   (LZX_NUM_SECONDARY_LENGTHS+1)
 59 #define LZX_LENGTH_TABLEBITS    (12)
 60 #define LZX_ALIGNED_MAXSYMBOLS  (LZX_ALIGNED_NUM_ELEMENTS)
 61 #define LZX_ALIGNED_TABLEBITS   (7)
 62 
 63 #define LZX_LENTABLE_SAFETY (64) /* we allow length table decoding overruns */
 64 
 65 #define LZX_DECLARE_TABLE(tbl) \
 66   UWORD tbl##_table[(1<<LZX_##tbl##_TABLEBITS) + (LZX_##tbl##_MAXSYMBOLS<<1)];\
 67   UBYTE tbl##_len  [LZX_##tbl##_MAXSYMBOLS + LZX_LENTABLE_SAFETY]
 68 
 69 struct LZXstate
 70 {
 71     UBYTE *window;         /* the actual decoding window              */
 72     ULONG window_size;     /* window size (32Kb through 2Mb)          */
 73     ULONG actual_size;     /* window size when it was first allocated */
 74     ULONG window_posn;     /* current offset within the window        */
 75     ULONG R0, R1, R2;      /* for the LRU offset system               */
 76     UWORD main_elements;   /* number of main tree elements            */
 77     int   header_read;     /* have we started decoding at all yet?    */
 78     UWORD block_type;      /* type of this block                      */
 79     ULONG block_length;    /* uncompressed length of this block       */
 80     ULONG block_remaining; /* uncompressed bytes still left to decode */
 81     ULONG frames_read;     /* the number of CFDATA blocks processed   */
 82     LONG  intel_filesize;  /* magic header value used for transform   */
 83     LONG  intel_curpos;    /* current offset in transform space       */
 84     int   intel_started;   /* have we seen any translatable data yet? */
 85 
 86     LZX_DECLARE_TABLE(PRETREE);
 87     LZX_DECLARE_TABLE(MAINTREE);
 88     LZX_DECLARE_TABLE(LENGTH);
 89     LZX_DECLARE_TABLE(ALIGNED);
 90 };
 91 
 92 /* LZX decruncher */
 93 
 94 /* Microsoft's LZX document and their implementation of the
 95  * com.ms.util.cab Java package do not concur.
 96  *
 97  * In the LZX document, there is a table showing the correlation between
 98  * window size and the number of position slots. It states that the 1MB
 99  * window = 40 slots and the 2MB window = 42 slots. In the implementation,
100  * 1MB = 42 slots, 2MB = 50 slots. The actual calculation is 'find the
101  * first slot whose position base is equal to or more than the required
102  * window size'. This would explain why other tables in the document refer
103  * to 50 slots rather than 42.
104  *
105  * The constant NUM_PRIMARY_LENGTHS used in the decompression pseudocode
106  * is not defined in the specification.
107  *
108  * The LZX document does not state the uncompressed block has an
109  * uncompressed length field. Where does this length field come from, so
110  * we can know how large the block is? The implementation has it as the 24
111  * bits following after the 3 blocktype bits, before the alignment
112  * padding.
113  *
114  * The LZX document states that aligned offset blocks have their aligned
115  * offset huffman tree AFTER the main and length trees. The implementation
116  * suggests that the aligned offset tree is BEFORE the main and length
117  * trees.
118  *
119  * The LZX document decoding algorithm states that, in an aligned offset
120  * block, if an extra_bits value is 1, 2 or 3, then that number of bits
121  * should be read and the result added to the match offset. This is
122  * correct for 1 and 2, but not 3, where just a huffman symbol (using the
123  * aligned tree) should be read.
124  *
125  * Regarding the E8 preprocessing, the LZX document states 'No translation
126  * may be performed on the last 6 bytes of the input block'. This is
127  * correct.  However, the pseudocode provided checks for the *E8 leader*
128  * up to the last 6 bytes. If the leader appears between -10 and -7 bytes
129  * from the end, this would cause the next four bytes to be modified, at
130  * least one of which would be in the last 6 bytes, which is not allowed
131  * according to the spec.
132  *
133  * The specification states that the huffman trees must always contain at
134  * least one element. However, many CAB files contain blocks where the
135  * length tree is completely empty (because there are no matches), and
136  * this is expected to succeed.
137  */
138 
139 
140 /* LZX uses what it calls 'position slots' to represent match offsets.
141  * What this means is that a small 'position slot' number and a small
142  * offset from that slot are encoded instead of one large offset for
143  * every match.
144  * - position_base is an index to the position slot bases
145  * - extra_bits states how many bits of offset-from-base data is needed.
146  */
147 static const UBYTE extra_bits[51] = {
148      0,  0,  0,  0,  1,  1,  2,  2,  3,  3,  4,  4,  5,  5,  6,  6,
149      7,  7,  8,  8,  9,  9, 10, 10, 11, 11, 12, 12, 13, 13, 14, 14,
150     15, 15, 16, 16, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17, 17,
151     17, 17, 17
152 };
153 
154 static const ULONG position_base[51] = {
155           0,       1,       2,      3,      4,      6,      8,     12,     16,     24,     32,       48,      64,      96,     128,     192,
156         256,     384,     512,    768,   1024,   1536,   2048,   3072,   4096,   6144,   8192,    12288,   16384,   24576,   32768,   49152,
157       65536,   98304,  131072, 196608, 262144, 393216, 524288, 655360, 786432, 917504, 1048576, 1179648, 1310720, 1441792, 1572864, 1703936,
158     1835008, 1966080, 2097152
159 };
160 
161 struct LZXstate *LZXinit(int window)
162 {
163     struct LZXstate *pState=NULL;
164     ULONG wndsize = 1 << window;
165     int i, posn_slots;
166 
167     /* LZX supports window sizes of 2^15 (32Kb) through 2^21 (2Mb) */
168     /* if a previously allocated window is big enough, keep it     */
169     if (window < 15 || window > 21) return NULL;
170 
171     /* allocate state and associated window */
172     pState = malloc(sizeof(struct LZXstate));
173     if (!(pState->window = malloc(wndsize)))
174     {
175         free(pState);
176         return NULL;
177     }
178     pState->actual_size = wndsize;
179     pState->window_size = wndsize;
180 
181     /* calculate required position slots */
182     if (window == 20) posn_slots = 42;
183     else if (window == 21) posn_slots = 50;
184     else posn_slots = window << 1;
185 
186     /** alternatively **/
187     /* posn_slots=i=0; while (i < wndsize) i += 1 << extra_bits[posn_slots++]; */
188 
189     /* initialize other state */
190     pState->R0  =  pState->R1  = pState->R2 = 1;
191     pState->main_elements   = LZX_NUM_CHARS + (posn_slots << 3);
192     pState->header_read     = 0;
193     pState->frames_read     = 0;
194     pState->block_remaining = 0;
195     pState->block_type      = LZX_BLOCKTYPE_INVALID;
196     pState->intel_curpos    = 0;
197     pState->intel_started   = 0;
198     pState->window_posn     = 0;
199 
200     /* initialise tables to 0 (because deltas will be applied to them) */
201     for (i = 0; i < LZX_MAINTREE_MAXSYMBOLS; i++) pState->MAINTREE_len[i] = 0;
202     for (i = 0; i < LZX_LENGTH_MAXSYMBOLS; i++)   pState->LENGTH_len[i]   = 0;
203 
204     return pState;
205 }
206 
207 void LZXteardown(struct LZXstate *pState)
208 {
209     if (pState)
210     {
211         free(pState->window);
212         free(pState);
213     }
214 }
215 
216 int LZXreset(struct LZXstate *pState)
217 {
218     int i;
219 
220     pState->R0  =  pState->R1  = pState->R2 = 1;
221     pState->header_read     = 0;
222     pState->frames_read     = 0;
223     pState->block_remaining = 0;
224     pState->block_type      = LZX_BLOCKTYPE_INVALID;
225     pState->intel_curpos    = 0;
226     pState->intel_started   = 0;
227     pState->window_posn     = 0;
228 
229     for (i = 0; i < LZX_MAINTREE_MAXSYMBOLS + LZX_LENTABLE_SAFETY; i++) pState->MAINTREE_len[i] = 0;
230     for (i = 0; i < LZX_LENGTH_MAXSYMBOLS + LZX_LENTABLE_SAFETY; i++)   pState->LENGTH_len[i]   = 0;
231 
232     return DECR_OK;
233 }
234 
235 
236 /* Bitstream reading macros:
237  *
238  * INIT_BITSTREAM    should be used first to set up the system
239  * READ_BITS(var,n)  takes N bits from the buffer and puts them in var
240  *
241  * ENSURE_BITS(n)    ensures there are at least N bits in the bit buffer
242  * PEEK_BITS(n)      extracts (without removing) N bits from the bit buffer
243  * REMOVE_BITS(n)    removes N bits from the bit buffer
244  *
245  * These bit access routines work by using the area beyond the MSB and the
246  * LSB as a free source of zeroes. This avoids having to mask any bits.
247  * So we have to know the bit width of the bitbuffer variable. This is
248  * sizeof(ULONG) * 8, also defined as ULONG_BITS
249  */
250 
251 /* number of bits in ULONG. Note: This must be at multiple of 16, and at
252  * least 32 for the bitbuffer code to work (ie, it must be able to ensure
253  * up to 17 bits - that's adding 16 bits when there's one bit left, or
254  * adding 32 bits when there are no bits left. The code should work fine
255  * for machines where ULONG >= 32 bits.
256  */
257 #define ULONG_BITS (sizeof(ULONG)<<3)
258 
259 #define INIT_BITSTREAM do { bitsleft = 0; bitbuf = 0; } while (0)
260 
261 #define ENSURE_BITS(n)                                                  \
262   while (bitsleft < (n)) {                                              \
263     bitbuf |= ((inpos[1]<<8)|inpos[0]) << (ULONG_BITS-16 - bitsleft);   \
264     bitsleft += 16; inpos+=2;                                           \
265   }
266 
267 #define PEEK_BITS(n)   (bitbuf >> (ULONG_BITS - (n)))
268 #define REMOVE_BITS(n) ((bitbuf <<= (n)), (bitsleft -= (n)))
269 
270 #define READ_BITS(v,n) do {                                             \
271   ENSURE_BITS(n);                                                       \
272   (v) = PEEK_BITS(n);                                                   \
273   REMOVE_BITS(n);                                                       \
274 } while (0)
275 
276 
277 /* Huffman macros */
278 
279 #define TABLEBITS(tbl)   (LZX_##tbl##_TABLEBITS)
280 #define MAXSYMBOLS(tbl)  (LZX_##tbl##_MAXSYMBOLS)
281 #define SYMTABLE(tbl)    (pState->tbl##_table)
282 #define LENTABLE(tbl)    (pState->tbl##_len)
283 
284 /* BUILD_TABLE(tablename) builds a huffman lookup table from code lengths.
285  * In reality, it just calls make_decode_table() with the appropriate
286  * values - they're all fixed by some #defines anyway, so there's no point
287  * writing each call out in full by hand.
288  */
289 #define BUILD_TABLE(tbl)                                                \
290   if (make_decode_table(                                                \
291     MAXSYMBOLS(tbl), TABLEBITS(tbl), LENTABLE(tbl), SYMTABLE(tbl)       \
292   )) { return DECR_ILLEGALDATA; }
293 
294 
295 /* READ_HUFFSYM(tablename, var) decodes one huffman symbol from the
296  * bitstream using the stated table and puts it in var.
297  */
298 #define READ_HUFFSYM(tbl,var) do {                                      \
299   ENSURE_BITS(16);                                                      \
300   hufftbl = SYMTABLE(tbl);                                              \
301   if ((i = hufftbl[PEEK_BITS(TABLEBITS(tbl))]) >= MAXSYMBOLS(tbl)) {    \
302     j = 1 << (ULONG_BITS - TABLEBITS(tbl));                             \
303     do {                                                                \
304       j >>= 1; i <<= 1; i |= (bitbuf & j) ? 1 : 0;                      \
305       if (!j) { return DECR_ILLEGALDATA; }                              \
306     } while ((i = hufftbl[i]) >= MAXSYMBOLS(tbl));                      \
307   }                                                                     \
308   j = LENTABLE(tbl)[(var) = i];                                         \
309   REMOVE_BITS(j);                                                       \
310 } while (0)
311 
312 
313 /* READ_LENGTHS(tablename, first, last) reads in code lengths for symbols
314  * first to last in the given table. The code lengths are stored in their
315  * own special LZX way.
316  */
317 #define READ_LENGTHS(tbl,first,last) do { \
318   lb.bb = bitbuf; lb.bl = bitsleft; lb.ip = inpos; \
319   if (lzx_read_lens(pState, LENTABLE(tbl),(first),(last),&lb)) { \
320     return DECR_ILLEGALDATA; \
321   } \
322   bitbuf = lb.bb; bitsleft = lb.bl; inpos = lb.ip; \
323 } while (0)
324 
325 
326 /* make_decode_table(nsyms, nbits, length[], table[])
327  *
328  * This function was coded by David Tritscher. It builds a fast huffman
329  * decoding table out of just a canonical huffman code lengths table.
330  *
331  * nsyms  = total number of symbols in this huffman tree.
332  * nbits  = any symbols with a code length of nbits or less can be decoded
333  *          in one lookup of the table.
334  * length = A table to get code lengths from [0 to syms-1]
335  * table  = The table to fill up with decoded symbols and pointers.
336  *
337  * Returns 0 for OK or 1 for error
338  */
339 
340 static int make_decode_table(ULONG nsyms, ULONG nbits, UBYTE *length, UWORD *table) {
341     register UWORD sym;
342     register ULONG leaf;
343     register UBYTE bit_num = 1;
344     ULONG fill;
345     ULONG pos         = 0; /* the current position in the decode table */
346     ULONG table_mask  = 1 << nbits;
347     ULONG bit_mask    = table_mask >> 1; /* don't do 0 length codes */
348     ULONG next_symbol = bit_mask; /* base of allocation for long codes */
349 
350     /* fill entries for codes short enough for a direct mapping */
351     while (bit_num <= nbits) {
352         for (sym = 0; sym < nsyms; sym++) {
353             if (length[sym] == bit_num) {
354                 leaf = pos;
355 
356                 if((pos += bit_mask) > table_mask) return 1; /* table overrun */
357 
358                 /* fill all possible lookups of this symbol with the symbol itself */
359                 fill = bit_mask;
360                 while (fill-- > 0) table[leaf++] = sym;
361             }
362         }
363         bit_mask >>= 1;
364         bit_num++;
365     }
366 
367     /* if there are any codes longer than nbits */
368     if (pos != table_mask) {
369         /* clear the remainder of the table */
370         for (sym = pos; sym < table_mask; sym++) table[sym] = 0;
371 
372         /* give ourselves room for codes to grow by up to 16 more bits */
373         pos <<= 16;
374         table_mask <<= 16;
375         bit_mask = 1 << 15;
376 
377         while (bit_num <= 16) {
378             for (sym = 0; sym < nsyms; sym++) {
379                 if (length[sym] == bit_num) {
380                     leaf = pos >> 16;
381                     for (fill = 0; fill < bit_num - nbits; fill++) {
382                         /* if this path hasn't been taken yet, 'allocate' two entries */
383                         if (table[leaf] == 0) {
384                             table[(next_symbol << 1)] = 0;
385                             table[(next_symbol << 1) + 1] = 0;
386                             table[leaf] = next_symbol++;
387                         }
388                         /* follow the path and select either left or right for next bit */
389                         leaf = table[leaf] << 1;
390                         if ((pos >> (15-fill)) & 1) leaf++;
391                     }
392                     table[leaf] = sym;
393 
394                     if ((pos += bit_mask) > table_mask) return 1; /* table overflow */
395                 }
396             }
397             bit_mask >>= 1;
398             bit_num++;
399         }
400     }
401 
402     /* full table? */
403     if (pos == table_mask) return 0;
404 
405     /* either erroneous table, or all elements are 0 - let's find out. */
406     for (sym = 0; sym < nsyms; sym++) if (length[sym]) return 1;
407     return 0;
408 }
409 
410 struct lzx_bits {
411   ULONG bb;
412   int bl;
413   UBYTE *ip;
414 };
415 
416 static int lzx_read_lens(struct LZXstate *pState, UBYTE *lens, ULONG first, ULONG last, struct lzx_bits *lb) {
417     ULONG i,j, x,y;
418     int z;
419 
420     register ULONG bitbuf = lb->bb;
421     register int bitsleft = lb->bl;
422     UBYTE *inpos = lb->ip;
423     UWORD *hufftbl;
424 
425     for (x = 0; x < 20; x++) {
426         READ_BITS(y, 4);
427         LENTABLE(PRETREE)[x] = y;
428     }
429     BUILD_TABLE(PRETREE);
430 
431     for (x = first; x < last; ) {
432         READ_HUFFSYM(PRETREE, z);
433         if (z == 17) {
434             READ_BITS(y, 4); y += 4;
435             while (y--) lens[x++] = 0;
436         }
437         else if (z == 18) {
438             READ_BITS(y, 5); y += 20;
439             while (y--) lens[x++] = 0;
440         }
441         else if (z == 19) {
442             READ_BITS(y, 1); y += 4;
443             READ_HUFFSYM(PRETREE, z);
444             z = lens[x] - z; if (z < 0) z += 17;
445             while (y--) lens[x++] = z;
446         }
447         else {
448             z = lens[x] - z; if (z < 0) z += 17;
449             lens[x++] = z;
450         }
451     }
452 
453     lb->bb = bitbuf;
454     lb->bl = bitsleft;
455     lb->ip = inpos;
456     return 0;
457 }
458 
459 int LZXdecompress(struct LZXstate *pState, unsigned char *inpos, unsigned char *outpos, int inlen, int outlen) {
460     UBYTE *endinp = inpos + inlen;
461     UBYTE *window = pState->window;
462     UBYTE *runsrc, *rundest;
463     UWORD *hufftbl; /* used in READ_HUFFSYM macro as chosen decoding table */
464 
465     ULONG window_posn = pState->window_posn;
466     ULONG window_size = pState->window_size;
467     ULONG R0 = pState->R0;
468     ULONG R1 = pState->R1;
469     ULONG R2 = pState->R2;
470 
471     register ULONG bitbuf;
472     register int bitsleft;
473     ULONG match_offset, i,j,k; /* ijk used in READ_HUFFSYM macro */
474     struct lzx_bits lb; /* used in READ_LENGTHS macro */
475 
476     int togo = outlen, this_run, main_element, aligned_bits;
477     int match_length, length_footer, extra, verbatim_bits;
478     int copy_length;
479 
480     INIT_BITSTREAM;
481 
482     /* read header if necessary */
483     if (!pState->header_read) {
484         i = j = 0;
485         READ_BITS(k, 1); if (k) { READ_BITS(i,16); READ_BITS(j,16); }
486         pState->intel_filesize = (i << 16) | j; /* or 0 if not encoded */
487         pState->header_read = 1;
488     }
489 
490     /* main decoding loop */
491     while (togo > 0) {
492         /* last block finished, new block expected */
493         if (pState->block_remaining == 0) {
494             if (pState->block_type == LZX_BLOCKTYPE_UNCOMPRESSED) {
495                 if (pState->block_length & 1) inpos++; /* realign bitstream to word */
496                 INIT_BITSTREAM;
497             }
498 
499             READ_BITS(pState->block_type, 3);
500             READ_BITS(i, 16);
501             READ_BITS(j, 8);
502             pState->block_remaining = pState->block_length = (i << 8) | j;
503 
504             switch (pState->block_type) {
505                 case LZX_BLOCKTYPE_ALIGNED:
506                     for (i = 0; i < 8; i++) { READ_BITS(j, 3); LENTABLE(ALIGNED)[i] = j; }
507                     BUILD_TABLE(ALIGNED);
508                     /* rest of aligned header is same as verbatim */
509 
510                 case LZX_BLOCKTYPE_VERBATIM:
511                     READ_LENGTHS(MAINTREE, 0, 256);
512                     READ_LENGTHS(MAINTREE, 256, pState->main_elements);
513                     BUILD_TABLE(MAINTREE);
514                     if (LENTABLE(MAINTREE)[0xE8] != 0) pState->intel_started = 1;
515 
516                     READ_LENGTHS(LENGTH, 0, LZX_NUM_SECONDARY_LENGTHS);
517                     BUILD_TABLE(LENGTH);
518                     break;
519 
520                 case LZX_BLOCKTYPE_UNCOMPRESSED:
521                     pState->intel_started = 1; /* because we can't assume otherwise */
522                     ENSURE_BITS(16); /* get up to 16 pad bits into the buffer */
523                     if (bitsleft > 16) inpos -= 2; /* and align the bitstream! */
524                     R0 = inpos[0]|(inpos[1]<<8)|(inpos[2]<<16)|(inpos[3]<<24);inpos+=4;
525                     R1 = inpos[0]|(inpos[1]<<8)|(inpos[2]<<16)|(inpos[3]<<24);inpos+=4;
526                     R2 = inpos[0]|(inpos[1]<<8)|(inpos[2]<<16)|(inpos[3]<<24);inpos+=4;
527                     break;
528 
529                 default:
530                     return DECR_ILLEGALDATA;
531             }
532         }
533 
534         /* buffer exhaustion check */
535         if (inpos > endinp) {
536             /* it's possible to have a file where the next run is less than
537              * 16 bits in size. In this case, the READ_HUFFSYM() macro used
538              * in building the tables will exhaust the buffer, so we should
539              * allow for this, but not allow those accidentally read bits to
540              * be used (so we check that there are at least 16 bits
541              * remaining - in this boundary case they aren't really part of
542              * the compressed data)
543              */
544             if (inpos > (endinp+2) || bitsleft < 16) return DECR_ILLEGALDATA;
545         }
546 
547         while ((this_run = pState->block_remaining) > 0 && togo > 0) {
548             if (this_run > togo) this_run = togo;
549             togo -= this_run;
550             pState->block_remaining -= this_run;
551 
552             /* apply 2^x-1 mask */
553             window_posn &= window_size - 1;
554             /* runs can't straddle the window wraparound */
555             if ((window_posn + this_run) > window_size)
556                 return DECR_DATAFORMAT;
557 
558             switch (pState->block_type) {
559 
560                 case LZX_BLOCKTYPE_VERBATIM:
561                     while (this_run > 0) {
562                         READ_HUFFSYM(MAINTREE, main_element);
563 
564                         if (main_element < LZX_NUM_CHARS) {
565                             /* literal: 0 to LZX_NUM_CHARS-1 */
566                             window[window_posn++] = main_element;
567                             this_run--;
568                         }
569                         else {
570                             /* match: LZX_NUM_CHARS + ((slot<<3) | length_header (3 bits)) */
571                             main_element -= LZX_NUM_CHARS;
572 
573                             match_length = main_element & LZX_NUM_PRIMARY_LENGTHS;
574                             if (match_length == LZX_NUM_PRIMARY_LENGTHS) {
575                                 READ_HUFFSYM(LENGTH, length_footer);
576                                 match_length += length_footer;
577                             }
578                             match_length += LZX_MIN_MATCH;
579 
580                             match_offset = main_element >> 3;
581 
582                             if (match_offset > 2) {
583                                 /* not repeated offset */
584                                 if (match_offset != 3) {
585                                     extra = extra_bits[match_offset];
586                                     READ_BITS(verbatim_bits, extra);
587                                     match_offset = position_base[match_offset] - 2 + verbatim_bits;
588                                 }
589                                 else {
590                                     match_offset = 1;
591                                 }
592 
593                                 /* update repeated offset LRU queue */
594                                 R2 = R1; R1 = R0; R0 = match_offset;
595                             }
596                             else if (match_offset == 0) {
597                                 match_offset = R0;
598                             }
599                             else if (match_offset == 1) {
600                                 match_offset = R1;
601                                 R1 = R0; R0 = match_offset;
602                             }
603                             else /* match_offset == 2 */ {
604                                 match_offset = R2;
605                                 R2 = R0; R0 = match_offset;
606                             }
607 
608                             rundest = window + window_posn;
609                             this_run -= match_length;
610 
611                             /* copy any wrapped around source data */
612                             if (window_posn >= match_offset) {
613                                 /* no wrap */
614                                  runsrc = rundest - match_offset;
615                             } else {
616                                 runsrc = rundest + (window_size - match_offset);
617                                 copy_length = match_offset - window_posn;
618                                 if (copy_length < match_length) {
619                                      match_length -= copy_length;
620                                      window_posn += copy_length;
621                                      while (copy_length-- > 0) *rundest++ = *runsrc++;
622                                      runsrc = window;
623                                 }
624                             }
625                             window_posn += match_length;
626  
627                             /* copy match data - no worries about destination wraps */
628                             while (match_length-- > 0) *rundest++ = *runsrc++;
629 
630                         }
631                     }
632                     break;
633 
634                 case LZX_BLOCKTYPE_ALIGNED:
635                     while (this_run > 0) {
636                         READ_HUFFSYM(MAINTREE, main_element);
637 
638                         if (main_element < LZX_NUM_CHARS) {
639                             /* literal: 0 to LZX_NUM_CHARS-1 */
640                             window[window_posn++] = main_element;
641                             this_run--;
642                         }
643                         else {
644                             /* match: LZX_NUM_CHARS + ((slot<<3) | length_header (3 bits)) */
645                             main_element -= LZX_NUM_CHARS;
646 
647                             match_length = main_element & LZX_NUM_PRIMARY_LENGTHS;
648                             if (match_length == LZX_NUM_PRIMARY_LENGTHS) {
649                                 READ_HUFFSYM(LENGTH, length_footer);
650                                 match_length += length_footer;
651                             }
652                             match_length += LZX_MIN_MATCH;
653 
654                             match_offset = main_element >> 3;
655 
656                             if (match_offset > 2) {
657                                 /* not repeated offset */
658                                 extra = extra_bits[match_offset];
659                                 match_offset = position_base[match_offset] - 2;
660                                 if (extra > 3) {
661                                     /* verbatim and aligned bits */
662                                     extra -= 3;
663                                     READ_BITS(verbatim_bits, extra);
664                                     match_offset += (verbatim_bits << 3);
665                                     READ_HUFFSYM(ALIGNED, aligned_bits);
666                                     match_offset += aligned_bits;
667                                 }
668                                 else if (extra == 3) {
669                                     /* aligned bits only */
670                                     READ_HUFFSYM(ALIGNED, aligned_bits);
671                                     match_offset += aligned_bits;
672                                 }
673                                 else if (extra > 0) { /* extra==1, extra==2 */
674                                     /* verbatim bits only */
675                                     READ_BITS(verbatim_bits, extra);
676                                     match_offset += verbatim_bits;
677                                 }
678                                 else /* extra == 0 */ {
679                                     /* ??? */
680                                     match_offset = 1;
681                                 }
682 
683                                 /* update repeated offset LRU queue */
684                                 R2 = R1; R1 = R0; R0 = match_offset;
685                             }
686                             else if (match_offset == 0) {
687                                 match_offset = R0;
688                             }
689                             else if (match_offset == 1) {
690                                 match_offset = R1;
691                                 R1 = R0; R0 = match_offset;
692                             }
693                             else /* match_offset == 2 */ {
694                                 match_offset = R2;
695                                 R2 = R0; R0 = match_offset;
696                             }
697 
698                             rundest = window + window_posn;
699                             this_run -= match_length;
700 
701                             /* copy any wrapped around source data */
702                             if (window_posn >= match_offset) {
703                                 /* no wrap */
704                                  runsrc = rundest - match_offset;
705                             } else {
706                                 runsrc = rundest + (window_size - match_offset);
707                                 copy_length = match_offset - window_posn;
708                                 if (copy_length < match_length) {
709                                      match_length -= copy_length;
710                                      window_posn += copy_length;
711                                      while (copy_length-- > 0) *rundest++ = *runsrc++;
712                                      runsrc = window;
713                                 }
714                             }
715                             window_posn += match_length;
716  
717                             /* copy match data - no worries about destination wraps */
718                             while (match_length-- > 0) *rundest++ = *runsrc++;
719 
720                         }
721                     }
722                     break;
723 
724                 case LZX_BLOCKTYPE_UNCOMPRESSED:
725                     if ((inpos + this_run) > endinp) return DECR_ILLEGALDATA;
726                     memcpy(window + window_posn, inpos, (size_t) this_run);
727                     inpos += this_run; window_posn += this_run;
728                     break;
729 
730                 default:
731                     return DECR_ILLEGALDATA; /* might as well */
732             }
733 
734         }
735     }
736 
737     if (togo != 0) return DECR_ILLEGALDATA;
738     memcpy(outpos, window + ((!window_posn) ? window_size : window_posn) - outlen, (size_t) outlen);
739 
740     pState->window_posn = window_posn;
741     pState->R0 = R0;
742     pState->R1 = R1;
743     pState->R2 = R2;
744 
745     /* intel E8 decoding */
746     if ((pState->frames_read++ < 32768) && pState->intel_filesize != 0) {
747         if (outlen <= 6 || !pState->intel_started) {
748             pState->intel_curpos += outlen;
749         }
750         else {
751             UBYTE *data    = outpos;
752             UBYTE *dataend = data + outlen - 10;
753             LONG curpos    = pState->intel_curpos;
754             LONG filesize  = pState->intel_filesize;
755             LONG abs_off, rel_off;
756 
757             pState->intel_curpos = curpos + outlen;
758 
759             while (data < dataend) {
760                 if (*data++ != 0xE8) { curpos++; continue; }
761                 abs_off = data[0] | (data[1]<<8) | (data[2]<<16) | (data[3]<<24);
762                 if ((abs_off >= -curpos) && (abs_off < filesize)) {
763                     rel_off = (abs_off >= 0) ? abs_off - curpos : abs_off + filesize;
764                     data[0] = (UBYTE) rel_off;
765                     data[1] = (UBYTE) (rel_off >> 8);
766                     data[2] = (UBYTE) (rel_off >> 16);
767                     data[3] = (UBYTE) (rel_off >> 24);
768                 }
769                 data += 4;
770                 curpos += 5;
771             }
772         }
773     }
774     return DECR_OK;
775 }
776 
777 #ifdef LZX_CHM_TESTDRIVER
778 int main(int c, char **v)
779 {
780     FILE *fin, *fout;
781     struct LZXstate state;
782     UBYTE ibuf[16384];
783     UBYTE obuf[32768];
784     int ilen, olen;
785     int status;
786     int i;
787     int count=0;
788     int w = atoi(v[1]);
789     LZXinit(&state, w);
790     fout = fopen(v[2], "wb");
791     for (i=3; i<c; i++)
792     {
793         fin = fopen(v[i], "rb");
794         ilen = fread(ibuf, 1, 16384, fin);
795         status = LZXdecompress(&state, ibuf, obuf, ilen, 32768);
796         switch (status)
797         {
798             case DECR_OK:
799                 printf("ok\n");
800                 fwrite(obuf, 1, 32768, fout);
801                 break;
802             case DECR_DATAFORMAT:
803                 printf("bad format\n");
804                 break;
805             case DECR_ILLEGALDATA:
806                 printf("illegal data\n");
807                 break;
808             case DECR_NOMEMORY:
809                 printf("no memory\n");
810                 break;
811             default:
812                 break;
813         }
814         fclose(fin);
815         if (++count == 2)
816         {
817             count = 0;
818             LZXreset(&state);
819         }
820     }
821     fclose(fout);
822 }
823 #endif
824 
