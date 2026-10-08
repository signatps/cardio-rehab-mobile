#include "crc16.h"

// Implementacja z EHO-MINI
//********************************
static u8 CRCLO, CRCHI, CRCLO_BT, CRCHI_BT;

void Crc_CCITT (u8 A)
{
    u8 crcA, crcB;

    crcA = CRCHI = CRCHI ^ A;
    crcA = ((crcA>>4) ^ CRCHI);
    CRCHI = CRCLO;
    CRCLO = crcA;
    crcA = crcB = (crcA<<4) | (crcA>>4);
    crcA = (crcA<<1) | (crcA>>7);
    CRCHI = (crcA & 0x1F) ^ CRCHI;
    CRCHI = (crcB & 0xF0) ^ CRCHI;
    CRCLO ^= 0xE0 & (crcB<<1);
}

void Crc_CCITT_BT (u8 A)
{
    u8 crcA, crcB;

    crcA = CRCHI_BT = CRCHI_BT ^ A;
    crcA = ((crcA>>4) ^ CRCHI_BT);
    CRCHI_BT = CRCLO_BT;
    CRCLO_BT = crcA;
    crcA = crcB = (crcA<<4) | (crcA>>4);
    crcA = (crcA<<1) | (crcA>>7);
    CRCHI_BT = (crcA & 0x1F) ^ CRCHI_BT;
    CRCHI_BT = (crcB & 0xF0) ^ CRCHI_BT;
    CRCLO_BT ^= 0xE0 & (crcB<<1);
}

u16 calc_block_crc ( u8 *ptr, u16 size )
{
    CRCHI = CRCLO = 0xFF;
    while ( size-- ) {
        Crc_CCITT ( *ptr++ );
    }
    return (CRCHI << 8) | CRCLO;
}

u16 calc_block_crc_BT ( u8 *ptr, u16 size )
{
    CRCHI_BT = CRCLO_BT = 0xFF;
    while ( size-- ) {
        Crc_CCITT_BT ( *ptr++ );
    }
    return (CRCHI_BT << 8) | CRCLO_BT;
}

void crc_set_init_value ( u16 val )
{
	CRCLO = (u8)((val >> 8) & 0xFF);
	CRCHI = (u8)(val & 0xFF);
}

void crc_set_init_value_BT ( u16 val )
{
	CRCLO_BT = (u8)((val >> 8) & 0xFF);
	CRCHI_BT = (u8)(val & 0xFF);
}

u16 crc_get_result ( void )
{
	return (CRCHI << 8) | CRCLO;
}

u16 crc_get_result_BT ( void )
{
	return (CRCHI_BT << 8) | CRCLO_BT;
}

void crc_init_table ( void )
{
}

/**********************************

//Implementacja zoptymalizowana
static const u16 poly = 0x1021;
static u16 table[256];
static u16 crc = 0xFFFF;

void crc_set_init_value ( u16 val )
{
	crc = val;
}

u16 crc_get_result ( void )
{
	return crc;
}

void Crc_CCITT (u8 data)
{
	crc = (u16)((crc << 8) ^ table[((crc >> 8) ^ (0xff & data))]);
}

u16 calc_block_crc ( u8 *ptr, u16 size )
{
	u16 len = size;
    crc = 0xFFFF;
    while ( len-- )
    {
        Crc_CCITT ( *ptr++ );
    }
    return crc;
}

void crc_init_table ( void )
{
	u16 temp, a, i, j;
	for (i = 0; i < 256; ++i)
	{
		temp = 0;
		a = (u16)(i << 8);
		for (j = 0; j < 8; ++j)
		{
			if (((temp ^ a) & 0x8000) != 0)
			{
				temp = (u16)((temp << 1) ^ poly);
			}
			else
			{
				temp <<= 1;
			}
			a <<= 1;
		}
		table[i] = temp;
	}
}
*/
