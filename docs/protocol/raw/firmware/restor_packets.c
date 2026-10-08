/*
 * restor_protocol.c
 *
 *  Created on: 14 wrz 2017
 *      Author: Marcin Antczak
 */

#include "ql_type.h"
#include "crc16.h"
#include "ql_stdlib.h"
#include "restor_packets.h"
#include "ql_trace.h"
#include "basic_func.h"

#define SET_PACKET_VAL(val) packet[packet_size] = (u8)val; packet_size++

#define PACKET_SIZE        1500
#define STX_BYTE           0x80
#define MINIMAL_PACKET_LEN 8
#define PACKET_HDR_SIZE    5

static char debug_msg[64];
static u8 packet[PACKET_SIZE];
static u16 packet_size;

const u8 valid_packet_app_type[] =
{
        APP_ACK            ,
        APP_INIT           ,
        APP_ECG_OFFLINE    ,
        APP_GET_SCP_INFO   ,
        APP_GET_SCP        ,
        APP_SCP_DONE       ,
        APP_GET_PULSE      ,
        APP_ECG_ONLINE     ,
        APP_ECG_ONLINE_STOP,
        APP_ECG_END,
		APP_GET,
		APP_ECG_OFFLINE_STOP
};

u16 get_packet_size(void)
{
    return packet_size;
}

u8* create_ack_packet(u16 frame_number)
{
    u16 crc;
    packet_size = 0;
    Ql_memset(packet,0,sizeof(packet));
    SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
    SET_PACKET_VAL(DEV_ACK);                   //TYPE - 1
    SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
    SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
    SET_PACKET_VAL(0x00);                         //MSG LEN LOW BYTE - 4
    SET_PACKET_VAL(0x00);                         //MSG LEN HIGH BYTE -5
    crc = calc_block_crc_BT(&packet[1],packet_size-1);
    SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - 6
    SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - 7
    return packet;
}

u8* create_cmd_error_packet(u16 frame_number, u8 *ptr, u16 size)
{
    u16 crc,i;
    packet_size = 0;
    Ql_memset(packet,0,sizeof(packet));
    SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
    SET_PACKET_VAL(DEV_ECG_CMD_ERROR);                    //TYPE - 1
    SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
    SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
    SET_PACKET_VAL((size&0xFF));                  //MSG LEN LOW BYTE - 4
    SET_PACKET_VAL(((size>>8)&0xFF));              //MSG LEN HIGH BYTE - 5
    for (i = 0; i < size; i++)
    {
        SET_PACKET_VAL(ptr[i]);
    }
    crc = calc_block_crc_BT(&packet[1],packet_size-1);
    SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - n-1 (11)
    SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - n (12)
    return packet;
}

u8* create_dev_error_packet(u16 frame_number, u8 *ptr, u16 size)
{
    u16 crc,i;
    packet_size = 0;
    Ql_memset(packet,0,sizeof(packet));
    SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
    SET_PACKET_VAL(DEV_ECG_DEV_ERROR);                //TYPE - 1
    SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
    SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
    SET_PACKET_VAL((size&0xFF));                  //MSG LEN LOW BYTE - 4
    SET_PACKET_VAL(((size>>8)&0xFF));              //MSG LEN HIGH BYTE - 5
    for (i = 0; i < size; i++)
    {
        SET_PACKET_VAL(ptr[i]);
    }
    crc = calc_block_crc_BT(&packet[1],packet_size-1);
    SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - n-1 (11)
    SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - n (12)
    return packet;
}

u8* create_ecg_offline_done_packet(u16 frame_number)
{
    u16 crc;
    packet_size = 0;
    Ql_memset(packet,0,sizeof(packet));
    SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
    SET_PACKET_VAL(DEV_ECG_OFFLINE_DONE);             //TYPE - 1
    SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
    SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
    SET_PACKET_VAL(0x00);                         //MSG LEN LOW BYTE - 4
    SET_PACKET_VAL(0x00);                         //MSG LEN HIGH BYTE - 5
    crc = calc_block_crc_BT(&packet[1],packet_size-1);
    SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - n-1 (6)
    SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - n (7)
    return packet;
}

u8* create_scp_info_done_packet(u16 frame_number, u32 file_size, u16 crcfile)
{
    u16 crc;
    packet_size = 0;
    Ql_memset(packet,0,sizeof(packet));
    SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
    SET_PACKET_VAL(DEV_SCP_INFO);                     //TYPE - 1
    SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
    SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
    SET_PACKET_VAL(0x07);                         //MSG LEN LOW BYTE - 4
    SET_PACKET_VAL(0x00);                         //MSG LEN HIGH BYTE - 5
    SET_PACKET_VAL(file_size & 0xFF);             //MSG LEN LOW BYTE - 6
    SET_PACKET_VAL((file_size >> 8) & 0xFF);      //MSG              - 7
    SET_PACKET_VAL((file_size >> 16) & 0xFF);     //MSG              - 8
    SET_PACKET_VAL((file_size >> 24) & 0xFF);     //MSG              - 9
    SET_PACKET_VAL(0x00);                         //MSG LEN HIGH BYTE - 10
    SET_PACKET_VAL((crcfile & 0xFF));             //CRC FILE  11
    SET_PACKET_VAL(((crcfile >>8) & 0xFF));       //CRC FILE  12

    crc = calc_block_crc_BT(&packet[1],packet_size-1);

    SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - n-1 (13)
    SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - n (14)
    return packet;
}

u8* create_scp_chunk_to_send_packet(u16 frame_number, u8 *ptr, u16 size)
{
    u16 crc,i;
    packet_size = 0;
    Ql_memset(packet,0,sizeof(packet));
    SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
    SET_PACKET_VAL(DEV_SCP_CHUNK);                //TYPE - 1
    SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
    SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
    SET_PACKET_VAL((size&0xFF));                  //MSG LEN LOW BYTE - 4
    SET_PACKET_VAL(((size>>8)&0xFF));              //MSG LEN HIGH BYTE - 5
    for (i = 0; i < size; i++)
    {
        SET_PACKET_VAL(ptr[i]);
    }
    crc = calc_block_crc_BT(&packet[1],packet_size-1);
    SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - n-1 (11)
    SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - n (12)
    return packet;
}

u8* create_pulse_packet(u16 frame_number, u8 pulse)
{
     u16 crc;
     packet_size = 0;
     Ql_memset(packet,0,sizeof(packet));
     SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
     SET_PACKET_VAL(DEV_PULS_VALUE);                   //TYPE - 1
     SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
     SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
     SET_PACKET_VAL(0x01);                         //MSG LEN LOW BYTE - 4
     SET_PACKET_VAL(0x00);                         //MSG LEN HIGH BYTE -5
     SET_PACKET_VAL(pulse);                        //MSG VALUE     -6
     crc = calc_block_crc_BT(&packet[1],packet_size-1);
     SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - 7
     SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - 8
     return packet;
}

u8* ecg_online_info(u16 frame_number, u16 avm, u8 nr_channels, u8 *ptr, u8 size)
{
     u16 crc, it;
     u16 msg_size = /*sizeof(avm)*/avm+nr_channels/*sizeof(nr_channels)*/+size;
     packet_size = 0;
     Ql_memset(packet,0,sizeof(packet));
     SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
     SET_PACKET_VAL(DEV_ECG_ONLINE_INFO);              //TYPE - 1
     SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
     SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
     SET_PACKET_VAL((msg_size&0xFF));              //MSG LEN LOW BYTE - 4
     SET_PACKET_VAL(((msg_size >> 8)&0xFF));       //MSG LEN HIGH BYTE -5

     SET_PACKET_VAL((avm&0xFF));
     SET_PACKET_VAL(((avm >> 8)&0xFF));
     SET_PACKET_VAL(nr_channels);

     for (it = 0; it < size; it++)
     {
         SET_PACKET_VAL(ptr[it]);
     }

     crc = calc_block_crc_BT(&packet[1],packet_size-1);
     SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - 7
     SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - 8
     return packet;
}

u8* ecg_online_data(u16 frame_number, u16 avm, u8 nr_channels, u8 *ptr, u8 size)
{
     u16 crc, it;
     u16 msg_size = /*sizeof(avm)*/avm+nr_channels/*sizeof(nr_channels)*/+size;
     packet_size = 0;
     Ql_memset(packet,0,sizeof(packet));
     SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
     SET_PACKET_VAL(DEV_ECG_ONLINE_DATA);              //TYPE - 1
     SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
     SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
     SET_PACKET_VAL((msg_size&0xFF));              //MSG LEN LOW BYTE - 4
     SET_PACKET_VAL(((msg_size >> 8)&0xFF));       //MSG LEN HIGH BYTE -5

     SET_PACKET_VAL((avm&0xFF));
     SET_PACKET_VAL(((avm >> 8)&0xFF));
     SET_PACKET_VAL(nr_channels);

     for (it = 0; it < size; it++)
     {
         SET_PACKET_VAL(ptr[it]);
     }

     crc = calc_block_crc_BT(&packet[1],packet_size-1);
     SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - 7
     SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - 8
     return packet;
}

u8* create_cmd_generic_packet(u16 frame_number, u8 cmd, u8 *ptr, u16 size)
{
    u16 crc,i;
    packet_size = 0;
    Ql_memset(packet,0,sizeof(packet));
    SET_PACKET_VAL(STX_BYTE);                     //PACKET START - 0
    SET_PACKET_VAL(cmd              );            //TYPE - cmd
    SET_PACKET_VAL((frame_number&0xFF));          //FRAME_NUMBER LOW BYTE - 2
    SET_PACKET_VAL(((frame_number >> 8)&0xFF));   //FRAME_NUMBER HIGH BYTE - 3
    SET_PACKET_VAL((size&0xFF));                  //MSG LEN LOW BYTE - 4
    SET_PACKET_VAL(((size>>8)&0xFF));              //MSG LEN HIGH BYTE - 5
    for (i = 0; i < size; i++)
    {
        SET_PACKET_VAL(ptr[i]);
    }
    crc = calc_block_crc_BT(&packet[1],packet_size-1);
    SET_PACKET_VAL((crc&0xFF));                   //CRC LOW BYTE - n-1 (11)
    SET_PACKET_VAL(((crc >> 8)&0xFF));            //CRC HIGH BYTE - n (12)
    return packet;
}

static bool is_valid_type_packet(u8 packet_type)
{
    bool ret = FALSE;
    u8 it;
    for (it = 0; it <NUMB_OF_ELEMS(valid_packet_app_type); it++)
    {
        if (valid_packet_app_type[it] == packet_type)
        {
            ret = TRUE;
            break;
        }
    }

    if (ret == FALSE)
    {
        Ql_memset(debug_msg,0,sizeof(debug_msg));
        Ql_sprintf(debug_msg," !!!!!!!!PACKET COMMAND IS NOT VALID. COMMAND %d\n",packet_type);
    }

    return ret;
}

static bool is_valid_packet_len(u8 packet_type, u16 data_packet_len)
{
    bool ret = FALSE;
    switch(packet_type)
    {
        case APP_ACK             : ret = (data_packet_len == 0)? TRUE : FALSE; break;
        case APP_INIT            : ret = ((data_packet_len == 10) || (data_packet_len == 12))? TRUE : FALSE; break;
        case APP_ECG_OFFLINE     : ret = (data_packet_len != 0)? TRUE : FALSE; break;
        case APP_GET_SCP_INFO    : ret = (data_packet_len == 0)? TRUE : FALSE; break;
        case APP_GET_SCP         : ret = (data_packet_len == 0)? TRUE : FALSE; break;
        case APP_SCP_DONE        : ret = (data_packet_len == 0)? TRUE : FALSE; break;
        case APP_GET_PULSE       : ret = (data_packet_len == 2)? TRUE : FALSE; break;
        case APP_ECG_ONLINE      : ret = (data_packet_len == 0)? TRUE : FALSE; break;
        case APP_ECG_ONLINE_STOP : ret = (data_packet_len == 0)? TRUE : FALSE; break;
        case APP_ECG_END         : ret = (data_packet_len == 0)? TRUE : FALSE; break;
        case APP_GET             : ret = (data_packet_len == 1)? TRUE : FALSE; break;
        case APP_ECG_OFFLINE_STOP: ret = (data_packet_len == 0)? TRUE : FALSE; break;
        default:
            break;
    }

    if (ret == FALSE)
    {
        Ql_memset(debug_msg,0,sizeof(debug_msg));
        Ql_sprintf(debug_msg," !!!!!!!!DATA PACKET LENGTH IS NOT VALID. COMMAND %d and LENGTH \n",packet_type, data_packet_len);
    }

    return ret;
}

static bool is_valid_packet_crc(u8 packet_type, u8 *ptr_data, u16 data_packet_size)
{
    u16 crc, crc_packet;
    bool ret = FALSE;
    u8 *ptr = ptr_data;
    u16 packet_size = data_packet_size + PACKET_HDR_SIZE /* without STX_BYTE */;

    crc = calc_block_crc_BT(ptr_data,packet_size);

    ptr+=packet_size;
    crc_packet = *ptr;
    ptr++;
    crc_packet |= ((*ptr) << 8);

    ret = (crc_packet == crc) ? TRUE : FALSE;

    if (ret == FALSE)
    {
        Ql_memset(debug_msg,0,sizeof(debug_msg));
        Ql_sprintf(debug_msg," !!!!!!!!PACKET CRC IS NOT VALID. COMMAND %d CRC %x EXP CRC %x\n",packet_type,crc_packet,crc);
    }
    return ret;
}

static u16 get_msg_size(u16 data_packet_size)
{
    return (data_packet_size+MINIMAL_PACKET_LEN);
}

static bool is_end_of_message(u16 data_packet_size, u32 len)
{
    return (get_msg_size(data_packet_size) == len);
}

s32 get_packet_app_len(void)
{
	return NUMB_OF_ELEMS(valid_packet_app_type);
}

u8 * get_valid_packet_app_type_ptr(void)
{
	return (u8*)valid_packet_app_type;
}

bool is_protocol_valid(u8 *pdata, u32 len, u8**pNextData, char **pdebug)
{
    bool ret = FALSE;
    u8 it = 0;
    u16 data_packet_len = pdata[it+IDX_MSG_LEN_LOW] | (pdata[it+IDX_MSG_LEN_HIGH] << 8);
    u8  packet_type = pdata[it+IDX_PACKET_TYPE];
    u8 *ptr_packet_data = &pdata[it+IDX_PACKET_TYPE];
    u8 packet_stx_byte = pdata[it+IDX_STX_BYTE];

    if ( (len >= MINIMAL_PACKET_LEN))
    {
        if ((packet_stx_byte == STX_BYTE))
        {
             if( (is_valid_type_packet(packet_type) == TRUE ) &&
                 (is_valid_packet_len(packet_type, data_packet_len)) == TRUE &&
                 is_valid_packet_crc(packet_type,ptr_packet_data,  data_packet_len) == TRUE )
            {
                if (is_end_of_message(data_packet_len,len))
                {
                    *pNextData = NULL;
                }
                else
                {
                    *pNextData=pdata+get_msg_size(data_packet_len);
                }
                ret = TRUE;
            }
        }
        else
        {
            Ql_memset(debug_msg,0,sizeof(debug_msg));
            Ql_sprintf(debug_msg," !!!!!!!!PACKET DOES NOT START WITH %x. STARTS BY %x\n",STX_BYTE,packet_stx_byte);
        }
    }
    else
    {
        Ql_memset(debug_msg,0,sizeof(debug_msg));
        Ql_sprintf(debug_msg," !!!!!!!!PROCESSED PACKET LENGTH (%d) IS LESS THAN MINIMAL_PACKET_LEN(%d) ALLOWED\n",len,MINIMAL_PACKET_LEN);
    }
    *pdebug = debug_msg;
    return ret;
}
